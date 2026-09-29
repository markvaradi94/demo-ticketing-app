package io.callisto.ticketing.booking.domain;

// Wraps the identifier only at the application/port boundary — Booking's own JPA
// @Id stays a plain Long, so this doesn't touch persistence mapping at all.
public record BookingId(Long value) {

	public BookingId {
		if (value == null) {
			throw new IllegalArgumentException("Booking id must not be null");
		}
	}

	public static BookingId of(Long value) {
		return new BookingId(value);
	}

}
