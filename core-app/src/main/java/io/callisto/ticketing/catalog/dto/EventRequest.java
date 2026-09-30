package io.callisto.ticketing.catalog.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record EventRequest(
		@NotBlank String name,
		@NotBlank String venueName,
		@Positive int venueCapacity,
		@NotNull @Positive BigDecimal pricePerSeat,
		@NotNull @Future Instant startTime) {
}
