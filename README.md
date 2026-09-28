# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-03-end`**.

## Prerequisites

- JDK 25 (Temurin recommended), on your `PATH`. The build does not auto-provision a
  toolchain — install it yourself before running anything below.
- Docker (Desktop, Podman, or Rancher), running. `./gradlew build` starts an ephemeral
  Postgres via Testcontainers for tests; `./gradlew bootRun` starts Postgres and
  MongoDB via `compose/docker-compose.yml` automatically (Spring Boot's Docker Compose
  support — no manual `docker compose up` needed).

## Build & test

```
./gradlew build
```

## Where things stand — Session 3: Persistence (end)

Carried over from `session-03-start` unchanged: `io.callisto.ticketing.catalog`'s
`Event`/`Venue` JPA mapping and auto-generated `Long` ids, `compose/docker-compose.yml`,
`GlobalExceptionHandler`, profile-based `BookingProperties`.

**Both planted bugs, fixed:**

- **N+1** — `EventRepository.findAll()` now carries `@EntityGraph(attributePaths =
  "venue")`, joining `Venue` into the same query instead of one `SELECT` per event.
  Proven, not just asserted: `EventRepositoryTest.fetchesAllEventsWithTheirVenuesInOneQuery()`
  reads Hibernate's own statement-count statistics
  (`spring.jpa.properties.hibernate.generate_statistics=true`) and asserts exactly one
  query fires for two events.
- **Lost update** — `Event` now carries `@Version`. Two concurrent
  `bookedSeats` writes on the same row now race on the version instead of silently
  overwriting each other; the loser throws `ObjectOptimisticLockingFailureException`,
  mapped by `GlobalExceptionHandler` to `409 Conflict`. Proven the same way the
  original bug was: `EventRepositoryTest.rejectsASecondSaveAgainstAStaleVersion()`
  deliberately breaks out of `@DataJpaTest`'s per-test transaction
  (`@Transactional(propagation = NOT_SUPPORTED)`) so two `save()` calls genuinely land
  in separate transactions — inside one shared transaction they'd share one identity
  map and never race, the same trap the original scratch-test reproduction hit.
- **A bug `@Version` itself uncovered, also fixed:** adding `@Version` changes how
  Spring Data decides "is this entity new" — from "is the id null" to "is the version
  null." `EventController.update()` used to rebuild a brand-new `Event` from scratch
  on every `PUT`, which left `version` null and made Spring Data treat an *update* as
  an *insert* on an id that already exists — the request now fails silently (the old
  value survives). Verified genuine by temporarily reverting the fix and watching
  `EventJourneyIntegrationTest`'s update assertion fail before reapplying it. Fixed by
  building off `existing.toBuilder()` (loaded via `findOrThrow`) instead of a fresh
  `Event.builder()`, carrying `id` *and* `version` forward.

**`Booking` is now persisted — this session's lab, done:**

- `Booking` is a real `@Entity` (`bookings` table in `schema.sql`); `BookingRepository`
  is now a bare `JpaRepository<Booking, Long>`, same shape as `EventRepository`.
  `Booking`'s id is auto-generated too, the same `@GeneratedValue(strategy =
  IDENTITY)` convention `Event`/`Venue` already established on `session-03-start` —
  `BookingController` no longer assigns an id itself at all.
- **`BookingStatus` converted from a sealed interface to an enum** (`PENDING` /
  `CONFIRMED` / `CANCELLED`), mapped with `@Enumerated(EnumType.STRING)` — a sealed
  interface of empty records has no natural JPA column mapping, and a plain enum is
  genuinely the better fit once persistence is real, not just a simplification. This
  was done as a whole-codebase refactor via Copilot/IDE tooling rather than by hand —
  the type is used well beyond `booking`: `RefundPolicy`'s exhaustive `switch`
  (session 1) and `BookingReportService`'s stream filters (session 1's lab) both
  needed updating too. That ripple is exactly the argument for doing this kind of
  rename with tooling that finds every usage, not manual find-replace.

**MongoDB-backed reviews, new `io.callisto.ticketing.review`:**

- `Review` is a `@Document`, not a JPA entity — `ReviewRepository extends
  MongoRepository<Review, String>`. Its id is where this session's id story flips: we
  assign it ourselves (`UUID.randomUUID()`) instead of leaving it null for MongoDB to
  generate its own ObjectId-backed string — the more realistic choice for a document
  a client might reference from outside Mongo. `Review.eventId` is a `Long`, matching
  `Event`'s real id.
- `POST /events/{eventId}/reviews` (rejects an unknown event, reusing
  `EventNotFoundException`), `GET /events/{eventId}/reviews` (list), `GET
  /events/{eventId}/reviews/summary` (average rating + count).
- The summary is a `@Aggregation` pipeline declared directly on the repository
  interface — `$match` by `eventId`, `$group` with `$avg`/`$sum` — mapped straight
  onto a record (`ReviewSummary`), no imperative aggregation code. Verified against a
  real Mongo container in `ReviewRepositoryTest`, including the empty-result case
  (`Optional.empty()` → the controller returns a zeroed summary rather than a 404).
- `AbstractIntegrationTest` now starts a `MongoDBContainer` alongside the existing
  `PostgreSQLContainer`, same singleton-shared-across-test-classes pattern.

**One more id-story note, since two different conventions now live side by side:**
`Event`/`Venue`/`Booking` (Postgres) get a real auto-increment id from the database;
`Review` (Mongo) gets a UUID we assign ourselves. Both are realistic — which one you'd
reach for in a real system depends on whether the store offers a good native generator
and whether the id ever needs to be known before the row exists, not on "relational
vs. document" as a blanket rule.

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it.

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1) | plain JUnit | no |
| **Controller** | `EventControllerTest`, `BookingControllerTest`, `ReviewControllerTest` | `@WebMvcTest` + `@MockitoBean` on every repository | no |
| **Persistence** | `EventRepositoryTest`, `BookingRepositoryTest` | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` | yes — real Postgres |
| **Persistence (Mongo)** | `ReviewRepositoryTest` | `@DataMongoTest` | yes — real MongoDB |
| **Integration** | `EventJourneyIntegrationTest`, `BookingJourneyIntegrationTest` | `@SpringBootTest` + real HTTP (`TestRestTemplate`) | yes — real Postgres |

A sixth, narrower shape: `BookingPropertiesProfileTest` tests `SpringApplication`'s
own profile-file-loading behavior, so it needs real bootstrap machinery — but not the
whole app. `@SpringBootTest(classes = MinimalConfig.class)` gets real profile loading
without pulling in JPA/web/Mongo (`ApplicationContextRunner` would skip profile-file
loading entirely, which is the one thing this test needs to verify).

`AbstractIntegrationTest` provides both databases: **one Postgres container and one
Mongo container, each started once, shared across every test class that needs them**
— not one per class. Each test class owns its own data (helper methods building fresh
rows), not the shared base. Splitting it any other way caused genuine cross-test-class
failures during development (Spring's test-context cache colliding with per-class
container lifecycles) before landing on this pattern.

Both integration tests stay from session 2, now backed by real Postgres instead of
in-memory — `EventJourneyIntegrationTest` covers the full Event CRUD lifecycle
(list/update/delete), `BookingJourneyIntegrationTest` covers event+booking
interaction. No new integration test for reviews: the persistence and controller
layers already cover that feature's real behavior (the Mongo aggregation, the HTTP
contract) without needing a third, more expensive test making the same claims.

**A test that deliberately breaks its own layer's isolation, and why:**
`EventRepositoryTest.rejectsASecondSaveAgainstAStaleVersion()` opts out of
`@DataJpaTest`'s automatic per-test rollback (`@Transactional(propagation =
NOT_SUPPORTED)`) so its two `save()` calls land in genuinely separate transactions.
That means its writes are real, uncleaned-up commits against the shared container —
unlike every other test in this file — so it deletes its own row at the end by hand
instead of relying on rollback.

**A real cross-test pollution bug, caught the same way the planted bugs were —
reproduced, diagnosed, then fixed, not just reasoned about:** once
`fetchesAllEventsWithTheirVenuesInOneQuery()` started asserting an exact row count,
it intermittently failed with an extra row. That traced back to
`BookingJourneyIntegrationTest`, which creates a real event over HTTP and books it,
but — unlike its sibling `EventJourneyIntegrationTest` — never deleted it, leaking a
permanent row into the Postgres container every test class in the suite shares. Fixed
on both sides: the integration test now deletes what it created, and the repository
test no longer trusts the shared table to contain only its own rows — it filters
`findAll()`'s result down to the ids it just saved before asserting on it.

## Homework

A standalone exercise, not a change to this repo. Model a small concurrency scenario
outside the ticketing domain — a warehouse's stock count is a good one: a `Stock`
entity with a `quantity` and `@Version`, two "concurrent" decrements racing on the
same row. Write a persistence-layer test that reproduces the race using the same
technique as `EventRepositoryTest.rejectsASecondSaveAgainstAStaleVersion()`: load the
same row twice, save the first change, then assert the second `save()` throws
`ObjectOptimisticLockingFailureException`. Use a plain `@DataJpaTest` with an
in-memory or Testcontainers database — the point is the two-separate-reads-then-two-
writes shape, not the specific database.

This reinforces the trickiest thing from this session — that reproducing a
concurrency bug in a test requires genuinely separate transactions, not just two
method calls that look concurrent — on a domain simple enough that the concurrency
logic itself is the only thing you have to think about.

Next up, Session 4: splitting into `catalog`/`booking`/`shared` modules (a Spring
Modulith), with `verify()` wired into the build and boundary violations planted for
the lab to find.
