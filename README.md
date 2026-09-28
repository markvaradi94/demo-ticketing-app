# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-03-start`**.

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

## Where things stand — Session 3: Persistence (start)

Carried over from Session 2, unchanged: `io.callisto.ticketing.catalog` (Event CRUD),
`io.callisto.ticketing.booking` (booking endpoints), `GlobalExceptionHandler`,
profile-based `BookingProperties`.

**New this session — real persistence:**

- `Event` and `Venue` are now genuine JPA entities (`@Entity`, `@Id`), with
  `Event → Venue` a real `@ManyToOne(fetch = LAZY, cascade = ALL)`. `EventRepository`
  is now a bare `interface ... extends JpaRepository<Event, String>` — the actual
  "swap the in-memory repository for JPA" moment promised since session 2. `Booking`
  and `BookingStatus` are untouched — persisting `Booking` is this session's **lab
  task**, not provided baseline.
- `compose/docker-compose.yml` — Postgres 17 + MongoDB 8, for local `bootRun`.
  `schema.sql` + `ddl-auto=validate` (no Flyway, per course convention).
- **Two bugs, planted deliberately, both verified genuine before shipping** (not just
  theoretical — confirmed with scratch tests, then removed, before committing):
  - **N+1** — `EventController.list()` already calls `event.getVenue().getName()` per
    event; now that `Venue` is lazy-loaded, that's N extra `SELECT`s with no artificial
    planting needed. Visible in the SQL log (`spring.jpa.show-sql=true` is on).
  - **Lost update** — `Event.bookedSeats`, incremented in `BookingController.create()`,
    with no `@Version` yet. Two concurrent bookings racing on the same event silently
    lose one write instead of summing correctly.

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it. Previously
(sessions 1-2) everything was one style of full-stack test — that was correct back
then, because there was no real persistence layer to isolate anything *from*. Now
there is, so the split becomes meaningful:

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1) | plain JUnit | no |
| **Controller** | `EventControllerTest`, `BookingControllerTest` | `@WebMvcTest` + `@MockitoBean` on every repository | no |
| **Persistence** | `EventRepositoryTest` | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` | yes — real Postgres |
| **Integration** | `EventJourneyIntegrationTest`, `BookingJourneyIntegrationTest` | `@SpringBootTest` + real HTTP (`TestRestTemplate`) | yes — real Postgres |

A fifth, narrower shape: `BookingPropertiesProfileTest` tests `SpringApplication`'s
own profile-file-loading behavior, so it needs real bootstrap machinery — but not the
whole app. `@SpringBootTest(classes = MinimalConfig.class)` gets real profile loading
without pulling in JPA/web/Mongo (`ApplicationContextRunner` would skip profile-file
loading entirely, which is the one thing this test needs to verify).

`AbstractIntegrationTest` provides the database: **one Postgres container, started
once, shared across every test class that needs it** — not one per class. Each test
class owns its own data (helper methods building fresh rows with random UUIDs), not
the shared base. Splitting it any other way caused genuine cross-test-class failures
during development (Spring's test-context cache colliding with per-class container
lifecycles) before landing on this pattern.

Both integration tests stay from session 2, now backed by real Postgres instead of
in-memory — `EventJourneyIntegrationTest` covers the full Event CRUD lifecycle
(list/update/delete), `BookingJourneyIntegrationTest` covers event+booking
interaction. Different feature areas, not redundant coverage of the same thing.

Next up: fix both planted bugs (`@EntityGraph`, `@Version`), persist `Booking`
(converting `BookingStatus` to an enum along the way), and add MongoDB-backed
`Review`s with an aggregation — landing on `session-03-end`.
