# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-05-end`**.

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

## Where things stand — Session 5: Hexagonal architecture (end)

Carried over from `session-04-end` unchanged: `catalog`, `review`, and `shared` —
this session is entirely inside `booking`, and runtime behavior still doesn't
change. Baseline from `session-05-start`: `booking` already split into
`domain`/`application`/`adapter`, with one deliberate gap — `BookingService` still
depended directly on `BookingRepository` (adapter-layer) and `catalog.EventRepository`,
no ports. See `session-05-start`'s README for the full reasoning on why the three
domain exceptions stay at `booking`'s root rather than inside `domain/`, and why
`BookingService.create` takes a `Booking`, not a `BookingRequest`.

**`BookingArchitectureTests` — an ArchUnit `layeredArchitecture()` rule, the
finer-grained sibling to session 4's `ModularityTests`:**

```java
@AnalyzeClasses(packages = "io.callisto.ticketing.booking", importOptions = ImportOption.DoNotIncludeTests.class)
class BookingArchitectureTests {

    @ArchTest
    static final ArchRule respectsHexagonalLayering = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("io.callisto.ticketing.booking.domain..")
            .layer("Application").definedBy("io.callisto.ticketing.booking.application..")
            .layer("Adapter").definedBy("io.callisto.ticketing.booking.adapter..")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter")
            .whereLayer("Adapter").mayNotBeAccessedByAnyLayer();

}
```

`@AnalyzeClasses` + `@ArchTest` is ArchUnit's own JUnit 5 integration — the reason
`archunit-junit5` specifically was added, not just `archunit`. `DoNotIncludeTests`
scopes the check to production code only; test classes legitimately reach into
internals to mock things, and that's not what this rule is about.
`consideringOnlyDependenciesInLayers()` restricts the analysis to dependencies
*between* the three defined layers, so `catalog`, `shared`, and code outside
`booking` entirely don't trip it.

**Run against `session-05-start`'s gap, it fails — five real violations, all naming
the same class:**

```
Architecture Violation [Priority: MEDIUM] - Rule 'Layered architecture ... where layer 'Adapter' may not be accessed by any layer' was violated (5 times):
Constructor <BookingService.<init>(BookingRepository, EventRepository, BookingProperties)> has parameter of type <...adapter.out.persistence.BookingRepository> in (BookingService.java:0)
Field <BookingService.bookings> has type <...adapter.out.persistence.BookingRepository> in (BookingService.java:0)
Method <BookingService.cancel(Long, Long)> calls method <BookingRepository.save(Object)> in (BookingService.java:54)
Method <BookingService.create(Booking)> calls method <BookingRepository.save(Object)> in (BookingService.java:42)
Method <BookingService.findOrThrow(Long, Long)> calls method <BookingRepository.findById(Object)> in (BookingService.java:58)
```

Confirmed by actually running the rule against the unfixed code before writing any
fix — same discipline as every planted violation this course has used.

**The fix — two outbound ports, `BookingService` depends on neither adapter
directly:**

- `BookingRepositoryPort` (`save`, `findById`) — implemented by
  `BookingRepositoryAdapter`, a package-private wrapper around the (also now
  package-private) Spring Data `BookingRepository`.
- `EventAvailabilityPort` — one method, `reserveSeats(Long eventId, int seatCount)`,
  shaped around the one capability `booking` actually needs from `catalog`, the same
  "narrow, purpose-built" reasoning behind session 4's `EventClient`. Implemented by
  `EventAvailabilityAdapter`, which still reaches `catalog.EventRepository` directly
  to read-then-save the incremented `bookedSeats` — same coupling as before, now
  behind a port. Session 6's `EventInventory` aggregate replaces just this one
  adapter class; `EventAvailabilityPort` and `BookingService` don't change at all
  when that happens — the concrete payoff for putting a port here today.

Neither adapter needs to be `public` — only the port interface does. Spring
instantiates and injects a package-private class by its declared interface type via
reflection regardless of visibility, so this is the same "compiler enforces it, not
just convention" trick session 4 used for `Service`/`Mapper` classes, now applied to
adapters two packages deeper.

**One behavior-shaped consequence worth calling out:** `BookingService.create` now
checks the too-many-seats rule *before* calling `EventAvailabilityPort` at all — that
rule doesn't need catalog, so there's no reason to reach through the port just to
fail a check that's entirely `booking`'s own. `BookingServiceTest` proves this
directly (`rejectsMoreSeatsThanAllowedWithoutEverCallingThePort` asserts
`events.reserveSeats(...)` is never invoked), not just that the exception gets thrown.

**Re-run `BookingArchitectureTests` after the fix: green.** Re-run `ModularityTests`
too — still green, untouched by any of this, since everything moved stayed inside
`booking`'s own package tree.

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it.

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1), `EventClientTest`, `EventServiceTest`, `BookingServiceTest`, `EventAvailabilityAdapterTest`, `ReviewServiceTest` | plain JUnit, repository/port mocked by hand | no |
| **Controller** | `EventControllerTest`, `BookingControllerTest`, `ReviewControllerTest` | `@WebMvcTest` + `@MockitoBean` on the service | no |
| **Persistence** | `EventRepositoryTest`, `BookingRepositoryTest` | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` | yes — real Postgres |
| **Persistence (Mongo)** | `ReviewRepositoryTest` | `@DataMongoTest` | yes — real MongoDB |
| **Integration** | `EventJourneyIntegrationTest`, `BookingJourneyIntegrationTest` | `@SpringBootTest` + real HTTP (`TestRestTemplate`) | yes — real Postgres |

`booking`'s test classes above live under the same subpackage as the class they
cover — `RefundPolicyTest`/`BookingReportServiceTest` in `booking.domain[.report]`,
`BookingServiceTest`/`BookingPropertiesProfileTest` in `booking.application`,
`BookingControllerTest` in `booking.adapter.in.web`, `BookingRepositoryTest` and
`EventAvailabilityAdapterTest` in `booking.adapter.out.{persistence,catalog}` —
mirroring main, same as `catalog`/`review` already did before session 5.

Three narrower shapes beyond that table: `BookingPropertiesProfileTest` tests
`SpringApplication`'s own profile-file-loading behavior, so it needs real bootstrap
machinery — but not the whole app. `@SpringBootTest(classes = MinimalConfig.class)`
gets real profile loading without pulling in JPA/web/Mongo (`ApplicationContextRunner`
would skip profile-file loading entirely, which is the one thing this test needs to
verify). `ModularityTests` and `BookingArchitectureTests` — architecture, not
behavior: no Spring context at all, just static analysis over compiled classes
(`ApplicationModules.verify()` for cross-module boundaries, ArchUnit's
`layeredArchitecture()` for `booking`'s internal layering). Neither fits
unit/controller/persistence/integration because neither tests what the code *does*,
they test how the code is *shaped* — a genuinely different category, not a smaller
version of the others.

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

A standalone exercise, not a change to this repo. Take any small project with at
least one non-trivial outbound dependency (a repository, an HTTP call to another
service, a file write — anything a use case reads from or writes to) and add
ArchUnit the way this session did: `archunit-junit5`, a `@AnalyzeClasses` test class
with a `layeredArchitecture()` rule defining your own layers. Confirm it passes on
your current structure, then deliberately have your application/use-case layer call
the concrete outbound dependency directly instead of through an interface, and watch
`mayNotBeAccessedByAnyLayer()` name the exact constructor parameter, field, and
method calls responsible. Then introduce the port interface, fix it, confirm green
again.

Same mechanic as watching `BookingService`'s five violations get named down to the
exact line here — the point is practicing "define the rule, watch it catch the real
thing, fix it" on unfamiliar code, one layer more granular than session 4's
module-level version of the same exercise.

Next up, Session 6: DDD — `EventInventory` as a proper aggregate owning the
no-overbooking invariant, replacing `EventAvailabilityAdapter`'s
read-then-save-and-hope-nobody-else-wrote-in-between with real invariant enforcement.
`Money`/`SeatNumber`/`BookingId` as value objects, domain events, an
overbooking-impossible test. `EventAvailabilityPort` and `BookingService` shouldn't
need to change at all — today's whole point.

