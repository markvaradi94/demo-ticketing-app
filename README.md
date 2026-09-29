# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-04-end`**.

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

## Where things stand — Session 4: Modulith (end)

Carried over from `session-03-end` unchanged: every endpoint, every entity, every
test's actual behavior — this whole session doesn't touch runtime behavior at all.
It's purely about how the code that already exists is *organized*, and about a new
test-time enforcement of that organization.

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

**Spring Modulith dependency** (`spring-modulith-starter-core` +
`spring-modulith-starter-test`, `2.1.1` — the release tracking Spring Boot 4.1.x,
confirmed resolving cleanly against this project's `4.1.1`) — carried over from
`session-04-start`, unused there.

**Controller → service → repository, in `booking` and `review`, carried over from
`session-04-start` unchanged** — every controller used to call its repository (or,
for `review`, `EventController`) directly, with DTO construction inline. `booking`
and `review` each have a package-private `Service` (business rules, works in domain
objects) and `Mapper` (pure DTO construction) between their controller and
repository. See `session-04-start`'s README for the full reasoning on why this
landed as baseline rather than lab work.

**`ModularityTests` — this session's actual new code, and it's five lines of
substance:**

```java
ApplicationModules modules = ApplicationModules.of(TicketingApplication.class);

@Test
void verifiesModularStructure() {
    modules.verify();
}
```

That's the whole test. It's pure static analysis over the compiled classes (ArchUnit
under the hood) — no Spring context, no database, fast enough to run on every build.
And because it's a plain JUnit test sitting in `src/test/java`, it's automatically
part of `./gradlew check` the moment it exists — "wiring `verify()` into the build"
needed no Gradle configuration at all, just writing the test.

**Run against `session-04-start`'s planted violation, it fails with a genuinely
precise message** — not a generic "something's wrong," the exact call sites, now
inside the service layer that landed on `session-04-start`:

```
Module 'review' depends on non-exposed type io.callisto.ticketing.catalog.dto.EventResponse within module 'catalog'!
Method <io.callisto.ticketing.review.ReviewService.create(...)> calls method <io.callisto.ticketing.catalog.dto.EventResponse.name()> in (ReviewService.java:35)
Module 'review' depends on non-exposed type io.callisto.ticketing.catalog.dto.EventResponse within module 'catalog'!
Method <io.callisto.ticketing.review.ReviewService.list(...)> calls method <...> in (ReviewService.java:41)
```

**The fix — not just "swap the dependency," a narrow module-API bean:** the obvious
patch is `ReviewService` depending on `EventRepository` instead of `EventController`
— that *would* satisfy `verify()`, since `EventRepository` sits at catalog's root.
But it hands `review` the entire repository — save, delete, findAll, everything —
for what's actually one read. `EventClient`, new in `catalog`, is the real fix: a
narrow, purpose-built bean exposing exactly `nameOf(Long)`, shaped the way a real
client call would look if catalog were ever a separate service (it isn't, in this
course — the discipline holds regardless). `ReviewService` depends on that instead.
`BookingService` still reaches `EventRepository` directly — it needs to *read and
mutate* `bookedSeats`, a deeper coupling than a lookup, and that one belongs to
session 6's DDD work (the `EventInventory` aggregate owning the no-overbooking
invariant), not something to paper over here with a wider client API than `review`
actually needs today. `EventClient` itself gets a small unit test (`EventClientTest`,
mocked `EventRepository`, no Spring context) since it's new logic, not just a
pass-through. Re-run `ModularityTests` after the fix: green, no other violations
anywhere in the codebase.

**Then, the live-coding half of the lab: the same `Service`/`Mapper` split, applied
to `catalog`.** `EventService`/`EventMapper` are new this branch — `EventController`
is now as thin as `BookingController`/`ReviewController` already were on
`session-04-start`. Every business rule that used to live in `EventController` moved
to `EventService`: not-found handling, and the `@Version`-preserving `toBuilder()`
update from session 3. `EventControllerTest` shrank to HTTP-shape-only, matching
`BookingControllerTest`/`ReviewControllerTest`'s shape; `EventServiceTest` is new,
covering the rules that moved out — including the update-preserves-version behavior,
now provable at the unit level in addition to the existing integration-level proof
(`EventRepositoryTest`, `EventJourneyIntegrationTest`).

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it.

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1), `EventClientTest`, `EventServiceTest`, `BookingServiceTest`, `ReviewServiceTest` | plain JUnit, repository mocked by hand | no |
| **Controller** | `EventControllerTest`, `BookingControllerTest`, `ReviewControllerTest` | `@WebMvcTest` + `@MockitoBean` on the service | no |
| **Persistence** | `EventRepositoryTest`, `BookingRepositoryTest` | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` | yes — real Postgres |
| **Persistence (Mongo)** | `ReviewRepositoryTest` | `@DataMongoTest` | yes — real MongoDB |
| **Integration** | `EventJourneyIntegrationTest`, `BookingJourneyIntegrationTest` | `@SpringBootTest` + real HTTP (`TestRestTemplate`) | yes — real Postgres |

Two narrower shapes beyond that table: `BookingPropertiesProfileTest` tests
`SpringApplication`'s own profile-file-loading behavior, so it needs real bootstrap
machinery — but not the whole app. `@SpringBootTest(classes = MinimalConfig.class)`
gets real profile loading without pulling in JPA/web/Mongo (`ApplicationContextRunner`
would skip profile-file loading entirely, which is the one thing this test needs to
verify). And `ModularityTests` — architecture, not behavior: no Spring context at
all, just `ApplicationModules.of(...).verify()` doing static analysis over the
compiled classes. It doesn't fit unit/controller/persistence/integration because it
isn't testing what the code *does*, it's testing how the code is *shaped* — a
genuinely different category, not a smaller version of the others.

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

A standalone exercise, not a change to this repo. Take any small project of your
own (or a fresh one — a few packages is enough) and add Spring Modulith the same way
this session did: `spring-modulith-starter-core` + `spring-modulith-starter-test`, a
`ModularityTests` class with `ApplicationModules.of(YourApplication.class).verify()`.
Confirm it passes on your current structure, then deliberately introduce one
violation — reach into another package's non-root class from a class in a different
top-level package — and watch `verify()`'s failure message name the exact call site.
Then fix it and confirm it's green again.

This reinforces the core mechanic from this session on unfamiliar code: architecture
rules that live in a test not only *state* an intention (a doc comment or a README
section can do that) but *catch* the moment someone violates it, with a message
precise enough to fix from — same as watching `ReviewService`'s violation get named
down to the line number here.

Next up, Session 5: hexagonal architecture inside `booking` —
`domain`/`application`/`adapter` layering, with ArchUnit rules wired into `check` the
same way `ModularityTests` just was. No further Gradle or top-level package-boundary
changes needed; this is all refinement *inside* the module structure this session
established.
