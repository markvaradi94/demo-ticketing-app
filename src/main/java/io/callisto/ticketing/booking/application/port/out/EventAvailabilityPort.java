package io.callisto.ticketing.booking.application.port.out;

import io.callisto.ticketing.booking.domain.SeatCount;

// Shaped as the two capabilities booking actually needs from catalog — not a generic
// findById/save pair, the same "narrow, purpose-built" call session 4's EventClient
// already established. The adapter behind this port still reaches into
// catalog.EventRepository directly, but the no-overbooking invariant itself now lives
// on the EventInventory aggregate, not as raw arithmetic in the adapter.
public interface EventAvailabilityPort {

	void reserveSeats(Long eventId, SeatCount seatCount);

	void releaseSeats(Long eventId, SeatCount seatCount);

}
