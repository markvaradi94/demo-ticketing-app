# ticketing

Reference project for **Modern Java Architecture & Cloud Deployment**, a 10-session,
30-hour course. Built session by session, in the order it's taught — this repo's
history is the course.

## Branch convention

Two branches per session: `session-NN-start` is what you check out before the
session begins, `session-NN-end` is the finished state after that session's live
coding and lab. You are currently on **`session-01-start`**.

## Prerequisites

- JDK 25 (Temurin recommended), on your `PATH`. The build does not auto-provision a
  toolchain — install it yourself before running anything below.

## Build & test

```
./gradlew build
```

## Where things stand — Session 1: Java 8 → 25

This branch has the bare project skeleton plus one deliberately old-style class:
`BookingReportService` (`io.callisto.ticketing.report`) — a mutable-bean input
(`LegacyBooking`), a mutable result type (`BookingReportResult`), nested loops, and
string comparisons for status. It works, and its tests
(`BookingReportServiceTest`) pass; it's just painful to read.

**Session 1's lab:** refactor `BookingReportService` using records, sealed types, and
streams. The provided tests must keep passing throughout. You'll use
`io.callisto.ticketing.domain.BookingStatus`, a sealed interface introduced earlier in
the session's live coding — see `session-01-end` for the finished shape once you're
done.
