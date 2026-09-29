# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-06-start`**.

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

## Where things stand — Session 6: DDD (start)

Unlike sessions 4 and 5, this branch needed almost no prep work. It's
`session-05-end` unchanged, plus three baseline value objects — because the actual
gap this session addresses was already sitting in `session-05-end`'s code, untouched,
never tested for:

**`EventAvailabilityAdapter.reserveSeats` never checks capacity — overbooking is
possible today, no concurrency required.** It reads `Event`, adds `seatCount` to
`bookedSeats`, saves. Nothing compares the result against `venue.capacity`. Session
3's `@Version` work only stops two concurrent writes from silently clobbering each
other — it was never a capacity check, and nothing else in this codebase is one
either. A single, sequential booking past capacity just succeeds. Reproducing this
for real (not just reasoning about it) is where today's session starts.

**Second gap, same shape:** `BookingService.cancel()` flips `Booking.status` to
`CANCELLED` but never releases the seats it held — `bookedSeats` only ever goes up.
Both gaps stay exactly as they are on this branch; neither is today's baseline work,
both are today's actual lesson.

**Baseline additions — three value objects in `booking.domain`, wired through
existing signatures, no new behavior:**

- `SeatCount` — wraps the `int` used at `EventAvailabilityPort`'s boundary.
  `EventAvailabilityPort.reserveSeats(Long eventId, SeatCount seatCount)`; the port's
  adapter converts back to `int` for the (still-buggy) arithmetic. `Booking.seatCount`
  itself stays a plain `int` — it's a JPA-mapped entity field, and wrapping it would
  mean an `@Embeddable` conversion that has nothing to do with today's actual lesson.
- `Money` — wraps `RefundPolicy`'s `BigDecimal` parameter and return type.
  `Booking` has no price/paid-amount field to give `Money` a more natural home yet
  (that's session 7's payment concern, not this one), so it's scoped to where a real
  consumer already exists.
- `BookingId` — wraps `Long` only at `BookingRepositoryPort`/`BookingService`'s
  method signatures (`findById`, `get`, `cancel`). `Booking.id` stays a plain
  `Long` `@Id` — this is an identifier value object at the application boundary, not
  a persistence-layer replacement, so it never touches JPA's own id mapping.
  `BookingController` constructs `BookingId.of(bookingId)` from the raw
  `@PathVariable Long` when calling the service.

All three are records — value objects, not entities, same convention as every DTO in
this codebase (`Booking`/`Event`/`Venue` stay Lombok classes with identity-based
`equals`/`hashCode` precisely because they aren't this). Each has a small dedicated
test (`SeatCountTest`, `MoneyTest`, `BookingIdTest`) proving its validation actually
rejects bad input — real logic, same reasoning as `EventClientTest`/
`EventAvailabilityAdapterTest` before it.

`BookingArchitectureTests` and `ModularityTests` both still pass — none of this
touched a package boundary, only method signatures inside layers that were already
allowed to talk to each other.

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it.

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1), `SeatCountTest`, `MoneyTest`, `BookingIdTest`, `EventClientTest`, `EventServiceTest`, `BookingServiceTest`, `EventAvailabilityAdapterTest`, `ReviewServiceTest` | plain JUnit, repository/port mocked by hand | no |
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


