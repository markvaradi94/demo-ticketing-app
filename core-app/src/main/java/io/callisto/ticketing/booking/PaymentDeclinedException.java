package io.callisto.ticketing.booking;

public class PaymentDeclinedException extends RuntimeException {

	public PaymentDeclinedException(String reference) {
		super("Payment declined for " + reference);
	}

}
