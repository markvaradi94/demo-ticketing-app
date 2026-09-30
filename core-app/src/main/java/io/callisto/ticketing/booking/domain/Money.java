package io.callisto.ticketing.booking.domain;

import java.math.BigDecimal;

public record Money(BigDecimal amount) {

	public static final Money ZERO = new Money(BigDecimal.ZERO);

	public Money {
		if (amount == null) {
			throw new IllegalArgumentException("Money amount must not be null");
		}
	}

	public static Money of(String amount) {
		return new Money(new BigDecimal(amount));
	}

	public Money multiply(BigDecimal factor) {
		return new Money(amount.multiply(factor));
	}

}
