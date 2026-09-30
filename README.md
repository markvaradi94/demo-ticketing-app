# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-07-start`**.

## Prerequisites

- JDK 25 (Temurin recommended), on your `PATH`. The build does not auto-provision a
  toolchain — install it yourself before running anything below.
- Docker (Desktop, Podman, or Rancher), running. `./gradlew build` starts ephemeral
  Postgres and MongoDB via Testcontainers for `core-app`'s tests; `./gradlew
  :core-app:bootRun` starts Postgres, MongoDB, and (new this session) RabbitMQ via
  `compose/docker-compose.yml` automatically (Spring Boot's Docker Compose support —
  no manual `docker compose up` needed).

## Build & test

This is a **multi-module build**, new this session — `./gradlew build` from the root
builds and tests every subproject (`core-app`, `payment-service`,
`notification-service`, `shared`). To run just one service:

```
./gradlew :core-app:bootRun
./gradlew :payment-service:bootRun
./gradlew :notification-service:bootRun
```

`core-app` still owns the ticketing domain and is the only one with a database or a
Testcontainers-backed test suite; `payment-service` and `notification-service` are
plain, fast-booting Spring Boot apps.

## Where things stand — Session 7: Distribution (start)

The biggest structural jump so far — real Gradle multi-module, two brand-new Spring
Boot applications, RabbitMQ added as infrastructure. Genuinely more baseline work
than any prior `-start` branch, because "separate deployable" isn't something that
happens gradually — see the architecture note this repo has carried since session 4:
Gradle multi-module and "separate service" are the same event, and this is where it
finally happens.

**`core-app` is the old single-module app, moved, not rewritten:** everything from
`session-06-end` — every entity, controller, service, port, adapter, domain event —
is unchanged. `git mv src core-app/src` and `git mv build.gradle.kts
core-app/build.gradle.kts` were the whole migration; no package or class inside it
moved.

**`build-logic`, a Gradle included build holding two convention plugins,** so three
Spring Boot apps stop repeating the same eleven lines of `plugins {}`/toolchain/Lombok
boilerplate: `ticketing.java-conventions` (Java 25 toolchain, JUnit Platform,
`-Xlint:deprecation`) and `ticketing.spring-boot-conventions` (applies the first, adds
the Spring Boot + dependency-management plugins, group/version, Lombok). Every
subproject's own `build.gradle.kts` now declares only what makes it different from
its siblings — `core-app`'s Postgres/Mongo/Modulith/ArchUnit dependencies,
`payment-service`'s much shorter list.

**`shared`, promoted from a Modulith *package* to a real Gradle *subproject*** — the
session 4 architecture note's other half finally lands too: a package inside
`core-app`'s jar is invisible to a different jar, so the moment `notification-service`
needs to compile against the same message shape `core-app` publishes, `shared` has to
be a real dependency, not a convention. It holds exactly two records
(`BookingCreatedMessage`, `BookingCancelledMessage`) and one constants class
(`BookingRoutingKeys`) — the contract both sides of the RabbitMQ boundary compile
against directly, not a JSON shape agreed on by convention.

**`payment-service` ships complete and working — supporting infrastructure for the
lesson, not the lesson itself:** a fake gateway, `POST /payments`, deciding its
outcome purely from the charged amount so the whole demo stays inside the domain
instead of needing an admin flag or test-only header: under €1,000 approves
instantly, €1,000–1,999.99 approves after a genuine 5-second sleep (what gives
today's Resilience4j timeout and the virtual-threads demo something real to block
on), €2,000+ declines with 402. `PaymentOutcomeTest` covers the classification;
`PaymentControllerTest` covers the instant paths only — the slow path is exercised
live in session 7's lesson itself, not paid for in every test run.

**`notification-service` is a skeleton on purpose** — boots, RabbitMQ/tracing
dependencies wired, `@SpringBootApplication` and nothing else. No `@RabbitListener`
exists yet, same "add the dependency unused first" pattern sessions 4 and 5 used for
their verification tools. Consuming `BookingCreatedMessage`/`BookingCancelledMessage`
is this session's lab, not baseline.

**RabbitMQ topology, half-declared on purpose:** `core-app` declares the topic
exchange (`booking.events`, via `BookingEventsExchangeConfig`) but nothing publishes
to it yet — `BookingService` doesn't know RabbitMQ exists on this branch. Queue
declaration and binding are deliberately left to `notification-service`, alongside
whatever `@RabbitListener` gets built there — a queue is a consumer-owned resource,
not something the publisher should dictate.

**Pricing, finally giving `Money` a real home:** `Event` gains `pricePerSeat`
(`BigDecimal`, defaulted to zero the same way `bookedSeats` already is, for test
helpers that don't care about price), threaded through `EventRequest`/
`EventResponse`/`EventMapper`/`schema.sql`, and exposed via a new
`EventClient.pricePerSeat(Long)` — same narrow, purpose-built shape as
`EventClient.nameOf`. This is what session 7's payment call actually charges: seat
count times price per seat, computed once the live-coding session wires
`BookingService` to call payment at all.

**Resilience4j (`resilience4j-spring-boot4` 2.4.0, verified against Boot 4.1.1 —
`-spring-boot4`, not `-spring-boot3`, is the correct artifact for this Boot version)
and tracing (`micrometer-tracing-bridge-brave` + `spring-boot-starter-actuator`,
100% sampling set explicitly, not Boot's 10% default) are dependencies on all three
services already, unused on `core-app` until the payment call exists to wrap, and
inert on `payment-service`/`notification-service` until real traffic crosses the
boundary — but the auto-configuration is already active, so trace/span IDs already
correlate in log output the moment any service handles a request.

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it. Table below
covers `core-app` — still the only subproject with a database or Testcontainers.
`payment-service` has its own small pair, `PaymentOutcomeTest` (plain JUnit, the
classification logic) and `PaymentControllerTest` (`@WebMvcTest`, the instant
approve/decline paths only — the 5-second slow path is real production behavior,
demoed live rather than paid for in every test run). `notification-service` has no
tests yet on this branch — nothing to test until the lab adds a listener.

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1), `SeatCountTest`, `MoneyTest`, `BookingIdTest`, `EventInventoryTest`, `BookingCancelledEventListenerTest`, `EventClientTest`, `EventServiceTest`, `BookingServiceTest`, `EventAvailabilityAdapterTest`, `ReviewServiceTest` | plain JUnit, repository/port mocked by hand | no |
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
interaction, plus session 6's two additions
(`rejectsABookingThatWouldExceedCapacity`, `cancellingABookingReleasesItsSeatsForReuse`).
No new integration test for reviews: the persistence and controller layers already
cover that feature's real behavior (the Mongo aggregation, the HTTP contract)
without needing a third, more expensive test making the same claims.

**Session 6's two new integration tests are reference-level, not lab output** —
writing a multi-step HTTP proof like `cancellingABookingReleasesItsSeatsForReuse()`
from scratch is realistically 10–15 minutes on its own, which is what was pushing
the lab's second task over its ~30-minute budget. In the lab, students build and
verify `BookingCancelledEventListener` with the fast unit-level tests
(`BookingCancelledEventListenerTest`, the updated `BookingServiceTest`) — genuinely
enough to prove the wiring is correct. The full HTTP-level proof is this branch's
target, confirmed working, and the *skill* of writing one becomes this session's
homework instead, practiced on an unrelated project rather than spending lab minutes
writing it against `ticketing` specifically.

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


