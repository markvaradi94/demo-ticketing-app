package io.callisto.ticketing.catalog.dto;

import java.time.Instant;

public record EventResponse(Long id, String name, String venueName, int venueCapacity, Instant startTime) {
}
