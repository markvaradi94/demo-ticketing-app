package io.callisto.ticketing.booking.adapter.out.catalog;

import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.domain.EventInventory;
import io.callisto.ticketing.booking.domain.SeatCount;
import io.callisto.ticketing.catalog.Event;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Package-private, same reasoning as BookingRepositoryAdapter. Still reaches
// catalog.EventRepository directly to load and persist an Event — but the invariant
// itself (can this many seats actually be reserved/released?) now lives on
// EventInventory, not as raw arithmetic here. This adapter's job is purely
// translation: Event <-> EventInventory, and persisting whatever the aggregate decided.
@Component
@RequiredArgsConstructor
class EventAvailabilityAdapter implements EventAvailabilityPort {

	private final EventRepository events;

	@Override
	public void reserveSeats(Long eventId, SeatCount seatCount) {
		Event event = findOrThrow(eventId);
		EventInventory reserved = toInventory(event).reserve(seatCount);
		events.save(event.withBookedSeats(reserved.bookedSeats()));
	}

	@Override
	public void releaseSeats(Long eventId, SeatCount seatCount) {
		Event event = findOrThrow(eventId);
		EventInventory released = toInventory(event).release(seatCount);
		events.save(event.withBookedSeats(released.bookedSeats()));
	}

	private Event findOrThrow(Long eventId) {
		return events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
	}

	private EventInventory toInventory(Event event) {
		return EventInventory.of(event.getId(), event.getVenue().getCapacity(), event.getBookedSeats());
	}

}
