package io.callisto.ticketing.booking;

public class BookingAlreadyCancelledException extends RuntimeException {

	public BookingAlreadyCancelledException(Long bookingId) {
		super("Booking " + bookingId + " is already cancelled");
	}

}
