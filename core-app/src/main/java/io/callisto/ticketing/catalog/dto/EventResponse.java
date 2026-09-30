package io.callisto.ticketing.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record EventResponse(Long id, String name, String venueName, int venueCapacity, BigDecimal pricePerSeat, Instant startTime) {
}
