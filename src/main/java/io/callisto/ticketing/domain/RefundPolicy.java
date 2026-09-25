package io.callisto.ticketing.domain;

import java.math.BigDecimal;

public final class RefundPolicy {

	private static final BigDecimal HALF = new BigDecimal("0.5");

	private RefundPolicy() {
	}

	public static BigDecimal refundAmount(BookingStatus status, BigDecimal paidAmount) {
		return switch (status) {
			case BookingStatus.Pending ignored -> paidAmount;
			case BookingStatus.Confirmed ignored -> paidAmount.multiply(HALF);
			case BookingStatus.Cancelled ignored -> BigDecimal.ZERO;
		};
	}

}
