package io.callisto.ticketing.booking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatCountTest {

	@Test
	void acceptsAPositiveValue() {
		assertThat(SeatCount.of(2).value()).isEqualTo(2);
	}

	@Test
	void rejectsZero() {
		assertThatThrownBy(() -> SeatCount.of(0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANegativeValue() {
		assertThatThrownBy(() -> SeatCount.of(-1)).isInstanceOf(IllegalArgumentException.class);
	}

}
