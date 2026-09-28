package io.callisto.ticketing.booking;

public class BookingAlreadyCancelledException extends RuntimeException {

	public BookingAlreadyCancelledException(String bookingId) {
		super("Booking " + bookingId + " is already cancelled");
	}

}
