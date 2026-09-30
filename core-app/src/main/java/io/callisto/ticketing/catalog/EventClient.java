package io.callisto.ticketing.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// The intended way another module reads catalog data — narrow and purpose-built, the
// shape a real client would have if catalog were ever a separate service (it isn't,
// in this course, but the discipline holds regardless of whether a module ever
// physically splits out). Depending on EventRepository directly exposes far more
// than any one caller needs and couples the caller to catalog's persistence choice,
// not just its public contract. BookingService still reaches into EventRepository
// directly to read *and mutate* bookedSeats — that's a deeper coupling than a simple
// lookup, and belongs to session 6's DDD work (the EventInventory aggregate,
// overbooking as an owned invariant), not something to paper over here with a wider
// client API than this module actually needs today.
@Component
@RequiredArgsConstructor
public class EventClient {

	private final EventRepository events;

	public String nameOf(Long eventId) {
		return events.findById(eventId)
				.map(Event::getName)
				.orElseThrow(() -> new EventNotFoundException(eventId));
	}

	public BigDecimal pricePerSeat(Long eventId) {
		return events.findById(eventId)
				.map(Event::getPricePerSeat)
				.orElseThrow(() -> new EventNotFoundException(eventId));
	}

}
