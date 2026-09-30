package io.callisto.ticketing.booking.domain;

import java.math.BigDecimal;

public final class RefundPolicy {

	private static final BigDecimal HALF = new BigDecimal("0.5");

	private RefundPolicy() {
	}

	public static Money refundAmount(BookingStatus status, Money paidAmount) {
		return switch (status) {
			case PENDING -> paidAmount;
			case CONFIRMED -> paidAmount.multiply(HALF);
			case CANCELLED -> Money.ZERO;
		};
	}

}
