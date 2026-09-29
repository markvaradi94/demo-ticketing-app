package io.callisto.ticketing.booking.application.port.out;

// Shaped as the one capability booking actually needs from catalog — not a generic
// findById/save pair, the same "narrow, purpose-built" call session 4's EventClient
// already established. The adapter behind this port still reaches into
// catalog.EventRepository directly to do it (read + mutate bookedSeats); session 6's
// EventInventory aggregate replaces just that one adapter class, not this port or
// BookingService.
public interface EventAvailabilityPort {

	void reserveSeats(Long eventId, int seatCount);

}
