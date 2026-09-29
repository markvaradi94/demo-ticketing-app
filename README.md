# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-06-end`**.

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

## Where things stand — Session 6: DDD (end)

Two real, previously-undiscovered gaps, both fixed. Neither needed to be planted —
both were sitting in `session-05-end`'s code untouched, exactly as documented on
`session-06-start`.

**Gap 1 — no capacity check, reproduced before it was fixed:**
`EventAvailabilityAdapterTest.refusesToExceedCapacity()` was written against the
unfixed adapter first and genuinely failed — no exception thrown, the arithmetic just
succeeded past capacity. Same discipline as every planted bug this course has used,
just with a bug nobody had to plant.

**The fix — `EventInventory`, the aggregate owning the invariant:**

```java
public record EventInventory(Long eventId, int capacity, int bookedSeats) {

    public EventInventory reserve(SeatCount seatCount) {
        int updated = bookedSeats + seatCount.value();
        if (updated > capacity) {
            throw new OverbookingException(eventId, capacity, bookedSeats, seatCount.value());
        }
        return new EventInventory(eventId, capacity, updated);
    }

    public EventInventory release(SeatCount seatCount) {
        return new EventInventory(eventId, capacity, Math.max(0, bookedSeats - seatCount.value()));
    }

}
```

Not separately persisted — built fresh from `catalog.Event`'s own
`capacity`/`bookedSeats` on every call, wrapping the existing row rather than adding
a second table. `EventAvailabilityAdapter` now does pure translation
(`Event` ↔ `EventInventory`, persist whatever the aggregate decided); the invariant
itself lives in one place, not as inline arithmetic. `OverbookingException` lives at
`booking`'s root, not `booking.domain` — learned that placement rule the hard way in
session 5 for the other three exceptions, applied correctly here from the start.

**The overbooking-impossible proof — `EventInventoryTest`, a fast domain-level unit
test, not a concurrent-threads test:** `refusesToExceedCapacity()` asserts the
aggregate itself throws, deterministically, no timing games required. Session 3's
`EventRepositoryTest.rejectsASecondSaveAgainstAStaleVersion()` already covers the
race-protection half (two concurrent writes can't silently clobber each other); this
covers the half that was missing entirely — a capacity ceiling that actually exists.
`GlobalExceptionHandler` maps `OverbookingException` to 409, same status as
`BookingAlreadyCancelledException` and a stale-version conflict — all "conflict with
current state" in the same family.

**Gap 2 — cancel never released seats, fixed with a domain event:**
`BookingService.cancel()` now publishes `BookingCancelled(eventId, seatCount)` via
`ApplicationEventPublisher` after its own save succeeds.
`BookingCancelledEventListener`, a plain `@EventListener` (not
`@TransactionalEventListener(phase = AFTER_COMMIT)` — `cancel()` isn't wrapped in an
explicit `@Transactional` today, so there's no transaction for `AFTER_COMMIT` to
defer past, and using it here would silently never fire without also opening a
transaction boundary this session doesn't need), calls
`EventAvailabilityPort.releaseSeats(eventId, seatCount)`, which runs `EventInventory`'s
`release()` the same way `reserveSeats` runs `reserve()`.

**Proof, end to end, not just unit-level:** two new
`BookingJourneyIntegrationTest` methods over real HTTP and real Postgres —
`rejectsABookingThatWouldExceedCapacity()` (book to capacity, a second booking gets
409) and `cancellingABookingReleasesItsSeatsForReuse()` (book to capacity, a second
booking is rejected, cancel the first, the *same* booking that just failed now
succeeds). Because the listener fires synchronously, the very next HTTP call already
sees the released capacity — no polling, no eventual consistency to wait out.

**Value objects — baseline from `session-06-start`, wired through existing
signatures, no new behavior on their own:**

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

## Homework

A standalone exercise, not a change to this repo. Take any small domain with a real
invariant (a warehouse's stock count not going negative, a wallet balance not going
below zero, a meeting room not double-booked) and build a small aggregate the way
`EventInventory` does here: an immutable type whose methods enforce the rule and
throw a domain-specific exception when it would be violated, wrapping whatever
already persists the underlying state rather than adding a new table for it. Prove
it with a fast, deterministic unit test — no threads, no timing, just call the method
with inputs that should be rejected and assert it throws. Then add one domain event:
publish it when the invariant-protected operation succeeds, and have a separate
listener react to it (release a hold, send a notification, log an audit entry —
anything that's a genuine side effect of the operation, not the operation itself).

**Then close the loop the lab didn't have time for:** write one end-to-end test
proving the whole chain actually happened — the operation succeeds, the event fires,
the listener's side effect is genuinely visible afterward. If your project has a web
layer, follow `BookingJourneyIntegrationTest.cancellingABookingReleasesItsSeatsForReuse()`'s
shape exactly: do the thing that should be blocked, watch it get rejected, perform
the operation that should unblock it, confirm the previously-rejected thing now
succeeds. If it doesn't have a web layer, the equivalent is calling the use case
twice in sequence and asserting on real state in between — the point is proving it
through observable behavior, not by inspecting internals.

Same mechanic as `EventInventoryTest.refusesToExceedCapacity()` and
`BookingCancelledEventListener` here — practicing "the invariant lives in one place,
and a real consequence gets to react to it separately," plus the full proof the lab
itself didn't have room for, on unfamiliar code.

Next up, Session 7: distribution — `payment-service` and `notification-service`
extracted as genuinely separate deployables, the point where Gradle multi-module and
"separate service" finally become the same event (sessions 4–6 deliberately stayed
one module precisely because that event hadn't happened yet). Resilience4j
(timeout/retry/circuit breaker) on the payment call, RabbitMQ topology with an
idempotent consumer and a dead-letter queue, Micrometer tracing across the now-real
service boundary. This is also where virtual threads get their first hands-on demo,
on the payment-service call specifically.


