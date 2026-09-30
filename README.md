# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-08-start`**.

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

## Where things stand — Session 8: Cloud Run (start)

A different kind of session, and a different kind of `-start` branch. Sessions 1–7
could all be fully built, tested, and verified by running `./gradlew build` — every
piece of infrastructure was Docker-on-a-laptop, reproducible for anyone, verifiable
by CI. Session 8 leaves that world: Cloud Run, Cloud SQL, Atlas, CloudAMQP, and
Secret Manager are real external services with real accounts behind them. This
branch's code diff is genuinely small — one dependency addition — because most of
this session's actual "setup" isn't code at all, it's provisioning real accounts,
documented below rather than committed to a branch.

**A real, confirmed incompatibility, worth documenting rather than working around
silently:** the obvious choice for Secret Manager — `spring-cloud-gcp-starter-secretmanager`
— does not support Spring Boot 4. It's not a missing-feature gap; released versions
(through 8.1.1) throw errors at startup from outdated `ConfigData` bootstrap
initialization logic that hasn't been updated for Boot 4's new lifecycle, confirmed
against this project's own open GitHub issue tracking the incompatibility. This
course has been strict about verifying every dependency against Boot 4.1.1 before
shipping it (Resilience4j needed the `-spring-boot4` artifact specifically, not
`-spring-boot3`; Spring AMQP's JSON converter needed the Jackson-3-native
`JacksonJsonMessageConverter`, not the deprecated `Jackson2` one) — this is the first
time that check came back negative outright. Session 8 hand-rolls Secret Manager
integration instead, using the plain `com.google.cloud:google-cloud-secretmanager`
client directly (unaffected by the Spring starter's bootstrap-lifecycle problem,
since it's not a Spring integration at all) via a custom `EnvironmentPostProcessor`
— live-coding content, not baseline, see `session-08-end`'s README.

**`shared` picks up a second kind of cross-service concern, its scope honestly
widened:** until now it held only RabbitMQ message contracts. Both `core-app` and
`notification-service` need the same secret-fetching mechanism on the `cloud`
profile — genuine shared infrastructure, the same "more than one deployable needs to
agree on or reuse" reasoning that put the message contracts there in the first
place, just a different flavor of it. `com.google.cloud:libraries-bom` (Google's own
BOM for aligning `google-cloud-*` artifact versions) and
`google-cloud-secretmanager` are added now, unused — same "dependency first, code
next session" pattern sessions 4, 5, and 7 all used for their own verification
tools. `shared` also gains a direct `spring-boot` (core jar only, no starter, no
Spring Boot Gradle plugin) dependency — the one jar `EnvironmentPostProcessor`'s
interface lives in; `shared` isn't itself a bootable app, so it needs the interface,
not the whole framework.

**Before you teach this — real provisioning, not code, and it has to happen ahead of
time:**

- **A GCP project**, with billing enabled (GCP requires a card for identity
  verification even to use Always Free services — worth saying plainly to students
  rather than promising "no card, ever"). Cloud Run's Always Free tier (2M
  requests/month, permanent, no trial credit needed) comfortably covers this
  session's actual traffic.
- **One small, shared Cloud SQL Postgres instance**, provisioned by the instructor
  days before class — instance creation takes several minutes, real lab time nobody
  should spend waiting. Cloud SQL has no free tier at all; this is the one piece
  with a genuine (small, one-time, instructor-only) cost. A database/schema per
  student on the same instance, deleted the same day the session ends.
- **MongoDB Atlas M0** — free forever, no credit card, per student. No change from
  how `review`'s persistence has worked since session 3; only the connection string
  moves from local to a real cluster.
- **CloudAMQP's "Little Lemur" plan** — free, no card, per student (1M
  messages/month is far beyond what a lab needs). Replaces the local
  `compose/docker-compose.yml` RabbitMQ once `core-app`/`notification-service` are
  running somewhere that can't reach `localhost:5672` anymore.

## Testing strategy — all four layers, explicitly

This is as much a course topic as the code itself: tests are split by what they
actually verify, using the lightest tool that can genuinely test it. Table below
covers `core-app` — still the only subproject with a database or a
Testcontainers-backed unit/persistence suite (`notification-service`'s one
integration test manages its own RabbitMQ container the same manual way, described
above, but doesn't fit neatly into this `core-app`-shaped table).
`payment-service` has its own small pair, `PaymentOutcomeTest` (plain JUnit, the
classification logic) and `PaymentControllerTest` (`@WebMvcTest`, the instant
approve/decline paths only — the 5-second slow path is real production behavior,
demoed live rather than paid for in every test run). `notification-service` adds
`BookingEventsListenerTest` (plain JUnit, the idempotency dedup logic — no broker
needed) and `BookingEventsListenerIntegrationTest` (the real-broker proof, described
above).

| Layer | Example | Tool | Database? |
|---|---|---|---|
| **Unit** | `RefundPolicyTest`, `BookingReportServiceTest` (session 1), `SeatCountTest`, `MoneyTest`, `BookingIdTest`, `EventInventoryTest`, `BookingCancelledEventListenerTest`, `EventClientTest`, `EventServiceTest`, `BookingServiceTest`, `EventAvailabilityAdapterTest`, `PaymentAdapterTest`, `ReviewServiceTest` | plain JUnit, repository/port mocked by hand | no |
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

