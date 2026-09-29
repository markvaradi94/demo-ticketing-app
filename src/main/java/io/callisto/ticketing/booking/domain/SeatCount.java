package io.callisto.ticketing.booking.domain;

// Value object, not an entity — no identity, just a validated quantity. A record,
// same convention as every other value object/DTO in this codebase (entities like
// Booking stay Lombok classes with identity-based equals/hashCode instead).
public record SeatCount(int value) {

	public SeatCount {
		if (value <= 0) {
			throw new IllegalArgumentException("Seat count must be positive, got " + value);
		}
	}

	public static SeatCount of(int value) {
		return new SeatCount(value);
	}

}
