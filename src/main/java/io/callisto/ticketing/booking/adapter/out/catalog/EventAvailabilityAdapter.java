package io.callisto.ticketing.booking.adapter.out.catalog;

import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.domain.SeatCount;
import io.callisto.ticketing.catalog.Event;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Package-private, same reasoning as BookingRepositoryAdapter. Still reaches
// catalog.EventRepository directly — reads *and mutates* bookedSeats, a deeper
// coupling than a lookup, and notably: never checks the venue's capacity before
// doing it. Nothing stops this from overbooking an event today. That's this
// session's actual gap, not yet fixed on this branch.
@Component
@RequiredArgsConstructor
class EventAvailabilityAdapter implements EventAvailabilityPort {

	private final EventRepository events;

	@Override
	public void reserveSeats(Long eventId, SeatCount seatCount) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
		events.save(event.withBookedSeats(event.getBookedSeats() + seatCount.value()));
	}

}
