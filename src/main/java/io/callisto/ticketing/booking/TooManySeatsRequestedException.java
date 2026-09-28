package io.callisto.ticketing.booking;

public class TooManySeatsRequestedException extends RuntimeException {

	public TooManySeatsRequestedException(int requested, int maxAllowed) {
		super("Requested " + requested + " seats, but at most " + maxAllowed + " are allowed per booking");
	}

}
