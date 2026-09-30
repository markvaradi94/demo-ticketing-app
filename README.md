# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-07-end`**.

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

## Where things stand — Session 7: Distribution (end)

`session-07-start` did the heavy structural lift (multi-module, two new services,
RabbitMQ infrastructure, pricing) — see its README for that. This session is where
those pieces actually get used: `core-app` calls `payment-service` for real,
protected by Resilience4j; a booking's lifecycle gets published to RabbitMQ and
`notification-service` genuinely consumes it, with an idempotency check and a
dead-letter queue; and the blocking payment call becomes this course's first
hands-on virtual-threads demo.

### Live coding — calling payment-service, protected

**The naive `@TimeLimiter` approach doesn't apply here, and that's worth teaching
directly:** `@TimeLimiter` enforces a timeout by racing a `CompletableFuture` against
a timer — it needs an async return type to have anything to act on. `PaymentAdapter`
stays a plain blocking `RestClient` call, matching how this whole codebase has stayed
synchronous throughout, so the real timeout protection lives where it actually can:
`PaymentClientConfig`'s `RestClient` bean, built with an explicit connect/read
timeout via `HttpClientSettings` (`payment-service.read-timeout=2s` — shorter than
payment-service's genuine 5-second slow path, on purpose, so there's something real
for `@Retry`/`@CircuitBreaker` to react to).

**`PaymentPort`/`PaymentAdapter`** — one method, `charge(String reference,
BigDecimal amount)`, same narrow shape as every other port this course has built.
`@Retry(name = "payment")` and `@CircuitBreaker(name = "payment")` decorate the
adapter method directly (Resilience4j's aspect order is fixed — Retry wraps
CircuitBreaker, not the other way around, regardless of annotation order). Both are
configured with small numbers in `application.properties` — a 4-call sliding window,
2 retry attempts — because Resilience4j's own defaults (100-call window,
100-call minimum) are sized for production traffic, not a classroom.

**A decline is not a fault, and both patterns are told so explicitly:**
`PaymentAdapter` catches a 402 response and throws `PaymentDeclinedException`
(booking's root package, applying session 5's placement lesson correctly from the
start — see `session-06-start`'s README for where that lesson was first learned the
hard way); both `resilience4j.retry.instances.payment.ignore-exceptions` and
`resilience4j.circuitbreaker.instances.payment.ignore-exceptions` name it explicitly.
payment-service correctly declining a large charge isn't the dependency being
unhealthy — it shouldn't cost a retry or count against the breaker the way a timeout
or a 5xx does.

**The flow, reordered to avoid needing a refund:** `BookingService.create()` reserves
seats *before* charging, not after — if the charge fails for any reason,
`events.releaseSeats(...)` undoes the reservation, reusing the exact method session
6 built for cancellation's seat-release. Charging before reserving would risk a
successful charge with no seats to show for it, and this fake gateway has no refund
endpoint to compensate with; reserving first means a failed payment simply means the
booking attempt never happened.

**`GlobalExceptionHandler` gains three mappings:** `PaymentDeclinedException` → 402,
matching payment-service's own status for the same outcome; `CallNotPermittedException`
(the circuit breaker refusing to even try) and `ResourceAccessException` (every retry
attempt hit the read timeout) both → 503, the honest answer once `@Retry` has already
exhausted its attempts — the dependency isn't available right now, not "something
about this specific request is wrong."

**The virtual threads demo — command-line config, not new code:** fire concurrent
`POST /events/{id}/bookings` requests against an event priced into payment-service's
slow bucket (seat count × price per seat between €1,000 and €1,999.99), first with
platform threads (the default), then with
`./gradlew :core-app:bootRun --args='--spring.threads.virtual.enabled=true
--payment-service.read-timeout=10s'` — the read-timeout override matters here
specifically: the resilience demo's 2s default would cut the block short before
there's anything to observe, so bump it past the genuine 5s sleep for this one
demo. Compare thread behavior under load between the two runs.

### Lab Task 1 — publish and consume, for real

**`BookingEventPublisherPort`, a new port distinct from session 6's
`ApplicationEventPublisher` usage:** `ApplicationEventPublisher` is Spring's own
in-process event bus — framework infrastructure, injected directly into
`BookingService`, no port, same category as a method call. RabbitMQ is a genuine
external system crossing a real network hop to a different deployable — more like
`PaymentPort`/`EventAvailabilityPort` than like in-process eventing, so it gets a
port too. `RabbitBookingEventPublisher` implements it, translating a `Booking` into
the matching `shared` message record and publishing via `RabbitTemplate` to the
exchange `session-07-start` already declared.

**One booking-cancelled moment, two independent reactions, two different
mechanisms:** `BookingService.cancel()` still publishes the in-JVM `BookingCancelled`
event (releases seats, unchanged since session 6) *and* now calls
`bookingEvents.publishCancelled(...)` (tells the outside world) — neither knows the
other exists.

**A message converter matters as much as the exchange:** Spring AMQP's default
`SimpleMessageConverter` falls back to JDK serialization for anything that isn't a
`String`/`byte[]`, which nothing on the consuming side could realistically decode.
Both `core-app` and `notification-service` register a `JacksonJsonMessageConverter`
bean — Jackson 3's replacement for the now-deprecated `Jackson2JsonMessageConverter`,
matching this Boot version — so messages travel as real JSON.
`notification-service` needed a new dependency for this to actually work at runtime,
not just compile: `spring-boot-starter-jackson`, since unlike `core-app`/
`payment-service` it has no web starter to pull Jackson 3 in transitively.

**`notification-service`'s own queues, not core-app's to dictate:**
`BookingEventsQueueConfig` declares two durable queues — one per message shape,
rather than one shared queue two listeners would compete over — bound to `core-app`'s
exchange with routing keys `booking.created`/`booking.cancelled`.
`BookingEventsListener` gets its first real `@RabbitListener` methods, logging a
simulated notification for each.

### Lab Task 2 — idempotency and a dead-letter queue

**In-memory idempotency, documented as a deliberate simplification:**
`BookingEventsListener.alreadyProcessed(Long)` — a `ConcurrentHashMap.newKeySet()`
of booking ids, `Set.add()` doing both the check and the insert atomically. A restart
forgets everything, and a second instance in a scaled-out deployment wouldn't share
this set at all; a real system would persist processed ids, or lean on the
notification side effect itself being naturally idempotent. Named honestly rather
than left as a silent gap.

**A message this service can never process shouldn't loop forever:**
`spring.rabbitmq.listener.simple.default-requeue-rejected=false` — the default is
`true`, which would requeue and redeliver an unhandled exception indefinitely.
`false`, combined with the created queue's `x-dead-letter-exchange`/
`-routing-key` arguments (`BookingEventsQueueConfig`), routes a genuinely
unprocessable message to `notification.booking-created.dlq` instead. Demoed by hand
via RabbitMQ's management UI (port 15672, already forwarded since
`session-07-start`) — publish a deliberately malformed body directly to the
exchange and watch it land in the DLQ rather than vanish or loop. Only wired for the
created queue this session; the same pattern applies to the cancelled queue, not
repeated here for lab time.

**The one test in this session proving the actual broker plumbing works, not just
that the code compiles:** `BookingEventsListenerIntegrationTest`, a real
`RabbitMQContainer` (managed the same way `AbstractIntegrationTest` manages
Postgres/Mongo — a manual static block, not the `@Testcontainers`/`@Container`
extension, which would need its own separate dependency), publishing a real
`BookingCreatedMessage` through the real exchange and routing key, asserting the
real listener genuinely received and processed it. `BookingJourneyIntegrationTest`,
by contrast, mocks both `PaymentPort` and `BookingEventPublisherPort` — payment-service
and RabbitMQ are infrastructure that test suite doesn't start, and a real
cross-service contract test is out of scope for this course.

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

## Homework

A standalone exercise, not a change to this repo. Take any project with an outbound
call to something that can be slow or fail (a real third-party API is ideal; a small
local HTTP server you control is fine too) and add Resilience4j the way this session
did: a `@Retry` and a `@CircuitBreaker` on the call, both configured with small,
demoable numbers rather than the library's own production-sized defaults. Prove the
circuit breaker genuinely opens — force enough consecutive failures to trip it
(sliding-window-size small enough to reach in a few calls), then confirm a
subsequent call fails immediately without even attempting the real call underneath.
Separately: if the call is genuinely blocking (not already `CompletableFuture`-based),
resist the urge to reach for `@TimeLimiter` — work out from first principles why it
wouldn't do anything useful there, the same reasoning this session applied to
`PaymentAdapter`, and configure a client-side read timeout instead.

Same mechanic as watching `PaymentAdapter`'s breaker open and payment-service's
slow path get cut short by a real timeout here — practicing "configure it small
enough to observe, then actually force the failure and watch the protection engage"
on unfamiliar code, not just trusting the annotation did something.

Next up, Session 8: Cloud Run — `bootBuildImage` producing a real container image
for each of the three services, Cloud SQL replacing local Postgres, Atlas replacing
local MongoDB, Secret Manager for what `application-local.properties` currently
holds in plain text. First session touching any actual cloud infrastructure — nothing
before this point has needed anything beyond a laptop and Docker.
