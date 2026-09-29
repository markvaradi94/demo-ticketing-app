package io.callisto.ticketing.booking.adapter.out.catalog;

import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.catalog.Event;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Package-private, same reasoning as BookingRepositoryAdapter. Still reaches
// catalog.EventRepository directly — reads *and mutates* bookedSeats, a deeper
// coupling than a lookup. Session 6's EventInventory aggregate replaces this one
// adapter class; EventAvailabilityPort and BookingService stay untouched when that
// happens — the whole point of putting a port here.
@Component
@RequiredArgsConstructor
class EventAvailabilityAdapter implements EventAvailabilityPort {

	private final EventRepository events;

	@Override
	public void reserveSeats(Long eventId, int seatCount) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
		events.save(event.withBookedSeats(event.getBookedSeats() + seatCount));
	}

}
