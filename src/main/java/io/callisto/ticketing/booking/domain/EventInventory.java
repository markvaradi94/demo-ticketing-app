package io.callisto.ticketing.booking.domain;

import io.callisto.ticketing.booking.OverbookingException;

// The aggregate owning the no-overbooking invariant. Not separately persisted — built
// fresh from catalog.Event's own capacity/bookedSeats on every operation, wrapping the
// existing row rather than adding a second table for this course's scope. Immutable:
// reserve()/release() return a new EventInventory rather than mutating this one.
public record EventInventory(Long eventId, int capacity, int bookedSeats) {

	public static EventInventory of(Long eventId, int capacity, int bookedSeats) {
		return new EventInventory(eventId, capacity, bookedSeats);
	}

	public EventInventory reserve(SeatCount seatCount) {
		int updated = bookedSeats + seatCount.value();
		if (updated > capacity) {
			throw new OverbookingException(eventId, capacity, bookedSeats, seatCount.value());
		}
		return new EventInventory(eventId, capacity, updated);
	}

	public EventInventory release(SeatCount seatCount) {
		return new EventInventory(eventId, capacity, Math.max(0, bookedSeats - seatCount.value()));
	}

}
