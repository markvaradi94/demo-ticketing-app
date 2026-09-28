# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-02-start`**.

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

New on this branch — the "provided" baseline for Session 2, built and tested, not a
lab task:

- `io.callisto.ticketing.catalog` — full Event CRUD, in-memory (`EventRepository`,
  `EventController`), with request/response DTOs and Bean Validation
  (`EventRequest`/`EventResponse`). `EventNotFoundException` is `@ResponseStatus`-based
  for now — deliberately, so the session's live coding has an ugly default error body
  to replace with `ProblemDetail`. `EventRepository` is deliberately in-memory, not a
  shortcut — its method names already mirror Spring Data's `CrudRepository`, so
  Session 3's first live-coding step (swap it for a real `JpaRepository` on Postgres)
  changes the implementation, not the shape `EventController` depends on.
- **Entities vs value objects:** `Event` and `Venue` have identity (an `id`
  looked up/stored by) and are plain Lombok classes —
  `@Getter @NoArgsConstructor @AllArgsConstructor @Builder(toBuilder = true)
  @EqualsAndHashCode(of = "id")` — not records. Records can't be JPA entities (no
  no-args constructor, immutable fields, can't proxy a `final` class), and separately,
  their all-fields `equals`/`hashCode` is wrong for identity semantics regardless of
  JPA. `BookingStatus` and `Seat` have no identity and stay plain records, same as
  every DTO.
- **Constructor injection:** `EventController` uses `@RequiredArgsConstructor` on its
  `private final` dependency field instead of a hand-written constructor.
- `EventControllerTest` — integration tests over real HTTP (`TestRestTemplate`),
  covering create/fetch/validate/delete.

**Session 2's lab:** booking endpoints (create, get, cancel), validated and correctly
status-coded, plus a global `ProblemDetail` error handler and a `booking` rules
`@ConfigurationProperties` that differs by profile (`local`/`cloud`) — see
`session-02-end` for the finished shape.

**Boot 4 note:** if you're writing HTTP integration tests, `TestRestTemplate` moved to
`org.springframework.boot.resttestclient`, needs `@AutoConfigureTestRestTemplate`
explicitly, and needs `spring-boot-restclient` on the test classpath — none of that is
pulled in automatically by `spring-boot-starter-webmvc-test` alone.
