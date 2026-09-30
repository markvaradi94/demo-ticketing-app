package io.callisto.ticketing.booking;

// Lives at booking's root, not booking.domain — same reason the other three
// exceptions do (see session-05's README): shared.GlobalExceptionHandler needs it,
// and a nested subpackage's types are internal by default under Spring Modulith's
// convention. Learned that the hard way once already; applying it here from the start.
public class OverbookingException extends RuntimeException {

	public OverbookingException(Long eventId, int capacity, int bookedSeats, int requestedSeats) {
		super("Event " + eventId + " has " + capacity + " seats total, " + bookedSeats +
				" already booked — cannot reserve " + requestedSeats + " more");
	}

}
