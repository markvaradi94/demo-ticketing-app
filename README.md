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
  refactored from the mutable-bean, imperative-loop version on `session-01-start` into
  a stream pipeline over the records `BookingLine` and `BookingReport`. Its tests
  (`BookingReportServiceTest`) cover the same scenarios as the start branch's — one
  test (null/missing-status tolerance) drops away, because a record can't be built
  from a null required field the way the old mutable bean could.

## Homework

A standalone exercise — a new project, not a change to this repo (next session starts
from a fresh checkout of `session-02-start`, so nothing committed here would carry
forward anyway). Domain: a library's late-fee calculator, deliberately unrelated to
ticketing so it's genuine practice, not copy-adjust.

1. Write a short **imperative first pass**: a mutable `Loan` bean (book title, due
   date, returned-or-not, etc.) and a `for` loop over a `List<Loan>` that sums a late
   fee per loan. Keep it small — this step exists to give yourself something real to
   refactor, the way `session-01-start`'s `LegacyBookingReportService` did.
2. **Refactor it**, the same way this session's lab turned that class into
   `BookingReportService`:
   - `Loan` becomes a record.
   - Fee status becomes a sealed interface `LoanStatus` — `OnTime`, `Overdue(int
     daysLate)`, `Lost` — each a record.
   - The `for` loop becomes a stream pipeline; the per-status fee rule becomes an
     **exhaustive pattern-matching `switch`** over `LoanStatus`, the same shape as
     `RefundPolicy` in this repo.

Use `RefundPolicy` and `BookingReportService` (both in this repo) as your pattern
reference for the exhaustive switch and the stream pipeline, respectively.

Next up, Session 2: Spring Boot's startup internals, real REST endpoints for
bookings, and the first GitHub Actions workflow.
