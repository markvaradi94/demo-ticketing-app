package io.callisto.ticketing.booking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingIdTest {

	@Test
	void wrapsTheValue() {
		assertThat(BookingId.of(7L).value()).isEqualTo(7L);
	}

	@Test
	void rejectsANullValue() {
		assertThatThrownBy(() -> new BookingId(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
