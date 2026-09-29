# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-04-start`**.

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

## Where things stand — Session 4: Modulith (start)

Carried over from `session-03-end` unchanged: every endpoint, every entity, every
test — this session doesn't touch behavior at all. It's purely about how the code
that already exists is *organized*, and about a new build-time (well, test-time)
enforcement of that organization.

**Package reorganization, so `catalog`/`booking`/`review`/`shared` are genuine
module boundaries, not just two of six:**

Before this branch, `io.callisto.ticketing` had *six* top-level packages —
`catalog`, `booking`, `review`, `web`, `report`, and a `domain` package that cut
across both `catalog` and `booking`'s actual concerns (`Event`/`Venue` conceptually
belong to catalog, `BookingStatus`/`RefundPolicy` to booking). Spring Modulith treats
every direct subpackage of the application's root package as its own module by
default, so that shape would've meant six modules, not the three or four the course
actually means. Moved, all via `git mv` to preserve history:

- `domain.Event`, `domain.Venue` → `catalog` (root package — catalog's public API)
- `domain.BookingStatus`, `domain.RefundPolicy` → `booking` (same reasoning)
- `report.*` (`BookingLine`, `BookingReport`, `BookingReportService`, session 1's
  standalone demo) → `booking.report` — it was always about bookings, just orphaned
- `web.GlobalExceptionHandler` → new `shared` package — genuinely cross-cutting,
  referenced by every other module, so it's the seed of what `shared` becomes
- `domain.Seat` — deleted. Session 1's third record example, never wired into the
  real model (bookings use a plain `seatCount`, not individual seats), and "seat
  maps" are explicitly out of scope for this course. No reason to find it a new home.

`review` needed no changes — it was already cleanly decoupled (only ever touches
other modules via a plain `Long eventId`, never an entity reference), which is
exactly the shape Spring Modulith rewards. It's a fourth module even though the
course's own shorthand only names three.

**Spring Modulith dependency added** (`spring-modulith-starter-core` +
`spring-modulith-starter-test`, `2.1.1` — the release tracking Spring Boot 4.1.x,
confirmed resolving cleanly against this project's `4.1.1`). Nothing uses it yet —
no `ApplicationModules.of(...).verify()` test exists on this branch. Writing that
test, watching it fail, and understanding *why* is this session's live-coding.

**Controller → service → repository, in `booking` and `review` — a second baseline
addition, not part of the Modulith lesson itself:** every controller used to call its
repository (or, for `review`, `EventController`) directly, with DTO construction
inline. `booking` and `review` now each have a package-private `Service` (business
rules, works in domain objects) and `Mapper` (pure DTO construction) between their
controller and repository — `BookingService`/`BookingMapper`,
`ReviewService`/`ReviewMapper`. `catalog` deliberately does **not** have this yet —
`EventController` still talks straight to `EventRepository`, exactly like every
controller did before this branch. That's on purpose: applying the same pattern to
`catalog` — the simplest of the three, no cross-module dependencies, straightforward
CRUD — is small enough to live-code in front of students during the session, once
they've already seen the shape twice (in `booking` and `review`) in the code they
were handed. Doing this same refactor for all three modules as hands-on lab work
would blow past this course's own "cap student-written code per lab at ~60 lines"
rule several times over.

**A boundary violation, planted deliberately** (same discipline as every other
planted bug in this course — verified to actually compile, run, and pass every
existing test *before* landing here, since Modulith violations are invisible to the
compiler and to every test that isn't the modularity test itself): `ReviewService`
depends on `EventController` directly instead of `EventRepository`, purely to reuse
its existing not-found check and read the event's name for `ReviewResponse.eventName`.
It compiles fine and every existing test passes — `EventController.get()`'s return
type, `EventResponse`, lives in `catalog.dto`, a *nested* package, not catalog's root.
Spring Modulith's default rule is that only root-package types are a module's public
API; nested packages are internal. So this is exactly the kind of mistake `verify()`
exists to catch: it's also a real anti-pattern independent of Modulith entirely
(a service depending on another module's controller instead of its repository, or a
purpose-built client) — two lessons in one deliberately small change. Finding and
fixing it — plus live-coding the same service/mapper split for `catalog` — is this
session's lab.

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it.

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1), `BookingServiceTest`, `ReviewServiceTest` | plain JUnit, repository mocked by hand | no |
| **Controller** | `EventControllerTest` (mocks `EventRepository` still), `BookingControllerTest`/`ReviewControllerTest` (mock their service) | `@WebMvcTest` + `@MockitoBean` | no |
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

Next up, Session 4's live coding and lab: write an `ApplicationModules.of(...).verify()`
test, watch it fail against `ReviewService`'s planted dependency on `EventController`,
and fix it; then live-code the same service/mapper split already in `booking` and
`review` for `catalog` — landing on `session-04-end`. That also sets up session 5
(hexagonal architecture inside `booking`) and session 6 (the `EventInventory` DDD
aggregate), neither of which needs any further Gradle or package-boundary changes —
both live entirely *inside* the module structure this branch just established.
