package io.callisto.ticketing.booking;

public class BookingNotFoundException extends RuntimeException {

	public BookingNotFoundException(Long bookingId) {
		super("No booking with id " + bookingId);
	}

}
