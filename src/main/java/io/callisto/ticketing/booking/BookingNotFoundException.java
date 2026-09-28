package io.callisto.ticketing.booking;

public class BookingNotFoundException extends RuntimeException {

	public BookingNotFoundException(String bookingId) {
		super("No booking with id " + bookingId);
	}

}
