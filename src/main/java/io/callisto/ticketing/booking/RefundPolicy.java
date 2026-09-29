package io.callisto.ticketing.booking;

import java.math.BigDecimal;

public final class RefundPolicy {

	private static final BigDecimal HALF = new BigDecimal("0.5");

	private RefundPolicy() {
	}

	public static BigDecimal refundAmount(BookingStatus status, BigDecimal paidAmount) {
		return switch (status) {
			case PENDING -> paidAmount;
			case CONFIRMED -> paidAmount.multiply(HALF);
			case CANCELLED -> BigDecimal.ZERO;
		};
	}

}
