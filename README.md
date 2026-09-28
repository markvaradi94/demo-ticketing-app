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
  automatically — base, `local`, and `cloud` each get their own `@SpringBootTest`
  context asserting the exact expected value, so this isn't just a manually-verified
  curl demo.
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
- **`BookingControllerTest`** — integration tests over real HTTP, covering the full
  create/fetch/cancel flow plus every error path above.
- **First CI workflow** — `.github/workflows/build.yml` runs `./gradlew build` on
  every push and pull request.

Next up, Session 3: Postgres/JPA and MongoDB persistence, and the N+1 /
missing-`@Version` bugs planted for that lab.
