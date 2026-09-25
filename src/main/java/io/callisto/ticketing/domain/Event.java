package io.callisto.ticketing.domain;

import java.time.Instant;

public record Event(String id, String name, Venue venue, Instant startTime) {
}
