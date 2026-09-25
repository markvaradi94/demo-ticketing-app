# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-01-end`**.

## Prerequisites

- JDK 25 (Temurin recommended), on your `PATH`. The build does not auto-provision a
  toolchain — install it yourself before running anything below.

## Build & test

```
./gradlew build
```

## Where things stand — Session 1: Java 8 → 25

This is the finished state of Session 1.

- `io.callisto.ticketing.domain` — `Event`, `Venue`, `Seat` as records, and
  `BookingStatus` as a sealed interface (`Pending` / `Confirmed` / `Cancelled`),
  introduced during the session's live coding. `RefundPolicy` shows it put to use in
  an exhaustive pattern-matching `switch`.
- `io.callisto.ticketing.report` — `BookingReportService`, the lab's outcome:
  refactored from the mutable-bean, nested-loop version on `session-01-start` into a
  stream pipeline over the records `BookingLine` and `BookingReport`. Its tests
  (`BookingReportServiceTest`) cover the same scenarios as the start branch's — one
  test (null/missing-status tolerance) drops away, because a record can't be built
  from a null required field the way the old mutable bean could.

Next up, Session 2: Spring Boot's startup internals, real REST endpoints for
bookings, and the first GitHub Actions workflow.
