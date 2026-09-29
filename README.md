# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-05-start`**.

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

## Where things stand — Session 5: Hexagonal architecture (start)

Carried over from `session-04-end` unchanged: `catalog`, `review`, and `shared` —
today's work is entirely inside `booking`. Runtime behavior doesn't change either;
this is another organization-and-enforcement session, same spirit as session 4, one
level more granular.

**`booking` split into `domain` / `application` / `adapter`, provided as baseline —
not today's lab, the folder move itself is mechanical:**

```
booking/
  domain/                    Booking, BookingStatus, RefundPolicy, report/*
  application/                BookingService, BookingProperties
  adapter/
    in/web/                  BookingController, BookingMapper (package-private), dto/*
    out/persistence/         BookingRepository (the bare Spring Data interface)
  BookingAlreadyCancelledException.java   ─┐
  BookingNotFoundException.java            ├─ kept at booking's root, not domain —
  TooManySeatsRequestedException.java     ─┘  see note below
```

`Booking` stays a JPA-annotated entity inside `domain/` — a pragmatic compromise,
not strict hexagonal orthodoxy, which would keep persistence annotations out of the
domain model entirely and map through a separate persistence type in the adapter.
Worth naming as a deliberate tradeoff rather than pretending it's textbook-pure.

**The three domain exceptions stay at `booking`'s root, not inside `domain/`** —
tried moving them in while building this branch, and `ModularityTests` (still
green from session 4, still running every build) immediately caught a real new
violation: `shared.GlobalExceptionHandler` maps them to HTTP status codes for the
whole app, and a nested subpackage's types are internal by default under Spring
Modulith's convention, so `shared` depending on them stopped being allowed the
moment they moved. Same pattern as `catalog.EventClient` and `catalog.EventNotFoundException`
already established: a module's own failure contract lives at its root, session 4's
convention held up under a finer-grained split without needing any change.

**One real fix made while doing the move, not lab content:** `BookingService.create`
used to take a `BookingRequest` DTO directly and call `BookingMapper.toNewBooking`
itself — harmless inside one flat package, but the moment `application` and
`adapter.in.web` became genuinely separate packages, that would have meant the
application layer importing an adapter-layer type, backwards. Fixed by flipping who
builds the domain object: `BookingController` now calls
`BookingMapper.toNewBooking(eventId, request)` itself and passes the resulting
`Booking` into `BookingService.create(Booking)` — the request DTO never crosses into
`application` at all, and `BookingService`'s test builds a `Booking` directly instead
of a `BookingRequest`, for the same reason.

**The gap this branch leaves on purpose:** `BookingService` still depends directly
on `BookingRepository` (now sitting in `adapter.out.persistence`) and on
`catalog.EventRepository` — no ports yet. Package-private visibility, the trick
session 4 leaned on to make illegal dependencies not compile, can't express this:
`application` and `adapter.out.persistence` are genuinely different packages now,
so anything crossing that boundary has to be `public`, and the compiler has nothing
to say about which direction that dependency runs. That's exactly the gap
today's session fills — outbound ports, adapters implementing them, and an ArchUnit
rule doing what package-private visibility can no longer do on its own.

**ArchUnit dependency** (`archunit-junit5`, `1.5.1`) — added to `build.gradle.kts`,
unused so far. No architecture test exists yet on this branch; writing one, and
watching it name today's gap the same precise way `ModularityTests` named session 4's,
is where the session starts.

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

`booking`'s test classes above now live under the same subpackage as the class they
cover — `RefundPolicyTest`/`BookingReportServiceTest` in `booking.domain[.report]`,
`BookingServiceTest`/`BookingPropertiesProfileTest` in `booking.application`,
`BookingControllerTest` in `booking.adapter.in.web`, `BookingRepositoryTest` in
`booking.adapter.out.persistence` — mirroring main, same as `catalog`/`review`
already did before today.

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

