# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-02-end`**.

## Prerequisites

- JDK 25 (Temurin recommended), on your `PATH`. The build does not auto-provision a
  toolchain — install it yourself before running anything below.

## Build & test

```
./gradlew build
```

## Where things stand — Session 2: Spring Boot core and REST

Carried over from Session 1: `io.callisto.ticketing.domain` (`Event`, `Venue`, `Seat`,
`BookingStatus`, `RefundPolicy`) and the refactored `BookingReportService`.

**Entities vs value objects, decided this session:** `Event`, `Venue`, and `Booking`
have identity (an `id` looked up/stored by) and are plain Lombok classes —
`@Getter @NoArgsConstructor @AllArgsConstructor @Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")` — not records. Records can't be JPA entities (no
no-args constructor, immutable fields, can't proxy a `final` class), and separately,
their all-fields `equals`/`hashCode` is wrong for identity semantics regardless of JPA.
Everything without identity — `BookingStatus`, `Seat`, `BookingLine`/`BookingReport`,
every DTO — stays a plain record.

**Constructor injection:** `EventController` and `BookingController` use
`@RequiredArgsConstructor` on their `private final` dependency fields instead of a
hand-written constructor — same rationale, less boilerplate for a well-understood
pattern.

Carried over from `session-02-start` — the "provided" baseline, unchanged:
`io.callisto.ticketing.catalog` (full Event CRUD, in-memory) and its tests. The
in-memory repositories (`EventRepository`, `BookingRepository`) are deliberate, not a
shortcut — their method names already mirror Spring Data's `CrudRepository`, so
Session 3's first live-coding step (swap them for a real `JpaRepository` on Postgres)
changes the implementation, not the shape the controllers depend on.

New on this branch:

- **Global error handling** — `io.callisto.ticketing.web.GlobalExceptionHandler`
  (`@RestControllerAdvice`) maps every domain exception to a `ProblemDetail` response:
  `EventNotFoundException`/`BookingNotFoundException` → 404,
  `TooManySeatsRequestedException` → 400, `BookingAlreadyCancelledException` → 409,
  Bean Validation failures → 400 with a field-level `errors` list. Note that
  `EventNotFoundException` dropped its `@ResponseStatus` annotation from
  `session-02-start` — the handler owns status codes now, not the exceptions.
- **`booking` rules config** — `BookingProperties` (`@ConfigurationProperties(prefix =
  "booking")`), read from `application.properties` (`maxSeatsPerBooking = 8`) and
  overridden per profile: `application-local.properties` (10),
  `application-cloud.properties` (6). Run with `--spring.profiles.active=local` or
  `=cloud` to see it change. `BookingPropertiesProfileTest` covers all three tiers
  automatically.
- **`io.callisto.ticketing.booking`** — the lab's outcome. `BookingController`:
  `POST /events/{eventId}/bookings` (create, 201 + `Location`, starts `Confirmed`),
  `GET /events/{eventId}/bookings/{bookingId}` (get), `POST
  /events/{eventId}/bookings/{bookingId}/cancel` (cancel, rejects a second cancel with
  409). In-memory `BookingRepository`, reusing `BookingStatus` from `session-01-end`'s
  live coding. `get`/`cancel` verify the booking's `eventId` actually matches the
  path's `eventId`, not just that the `bookingId` exists — a nested URL implies that
  parent-child relationship, so it has to be enforced, not just shaped that way. A
  mismatch returns the same 404 as an unknown booking, deliberately, rather than
  revealing that the ID exists under a different event.
- **First CI workflow** — `.github/workflows/build.yml` runs `./gradlew build` on
  every push and pull request.

## Testing strategy — three layers, on purpose

- `EventControllerTest`/`BookingControllerTest` — controller/HTTP-contract layer:
  `@WebMvcTest`, every repository/service dependency mocked (`@MockitoBean`). No
  database, fast. Verifies status codes, JSON shape, Bean Validation, routing.
- `BookingJourneyIntegrationTest` — full-stack integration layer: real HTTP
  (`TestRestTemplate`) through the whole app — create an event, book it, fetch it,
  cancel it — backed by the real (in-memory, for now) repositories. One broad
  happy-path journey, not edge cases — those are the controller tests' job.
- `BookingPropertiesProfileTest` — a narrower shape: this is testing
  `SpringApplication`'s own profile-file-loading behavior, so it needs real bootstrap
  machinery (`@SpringBootTest(classes = MinimalConfig.class)`), just scoped down to
  skip JPA/web/Mongo it doesn't need.

Why the controller and integration tests both exist: they test different things. The
controller test proves the HTTP contract is right even if the repository were swapped
out entirely; the integration test proves the pieces actually wire together. Neither
substitutes for the other. Nothing about the integration test's *shape* will change
when session 3 swaps in a real Postgres-backed repository — that's the point of
testing through the repository's public contract rather than its implementation.

**Boot 4 API locations, if you're writing more of these:** `TestRestTemplate` is in
`org.springframework.boot.resttestclient` (needs `@AutoConfigureTestRestTemplate` and
`spring-boot-restclient` on the test classpath). `WebMvcTest` is in
`org.springframework.boot.webmvc.test.autoconfigure`. Mocking a bean is
`@MockitoBean` (`org.springframework.test.context.bean.override.mockito`), not the
deprecated `@MockBean`. None of this is pulled in automatically by
`spring-boot-starter-webmvc-test` alone.

## Homework

A standalone exercise, not a change to this repo — next session starts from a fresh
checkout of `session-03-start`, so anything committed here wouldn't carry forward
anyway. Starting from a **new `start.spring.io` project** (Spring Boot 4.1.1, Java 25,
Web), build one paginated `GET` endpoint over a small in-memory list of your choosing,
and write a `@WebMvcTest` for it — mock the data source, assert on the HTTP response's
shape and paging metadata. Use `EventControllerTest` in this repo as your pattern
reference for how a controller test mocks its dependency and asserts on `MockMvc`
results.

Building it as its own small project, rather than inside this one, is deliberate: it
practices the whole skill — initializing a project, wiring the one dependency you
need, writing the test — not just editing inside something already assembled.

Next up, Session 3: Postgres/JPA and MongoDB persistence, and the N+1 /
missing-`@Version` bugs planted for that lab.
