package io.callisto.ticketing.booking.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

	@Test
	void multipliesTheAmount() {
		Money doubled = Money.of("20.00").multiply(new BigDecimal("2"));

		assertThat(doubled.amount()).isEqualByComparingTo("40.00");
	}

	@Test
	void zeroIsActuallyZero() {
		assertThat(Money.ZERO.amount()).isEqualByComparingTo("0");
	}

	@Test
	void rejectsANullAmount() {
		assertThatThrownBy(() -> new Money(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
