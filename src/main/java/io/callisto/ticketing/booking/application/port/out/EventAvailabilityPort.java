package io.callisto.ticketing.booking.application.port.out;

import io.callisto.ticketing.booking.domain.SeatCount;

// Shaped as the one capability booking actually needs from catalog — not a generic
// findById/save pair, the same "narrow, purpose-built" call session 4's EventClient
// already established. The adapter behind this port still reaches into
// catalog.EventRepository directly, and still doesn't check capacity at all — that's
// this session's gap to find and fix, not something already handled here.
public interface EventAvailabilityPort {

	void reserveSeats(Long eventId, SeatCount seatCount);

}
