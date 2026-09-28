package io.callisto.ticketing.booking;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingPropertiesProfileTest {

	@Autowired
	private BookingProperties bookingProperties;

	@Test
	void usesTheBaseLimitWhenNoProfileIsActive() {
		assertThat(bookingProperties.maxSeatsPerBooking()).isEqualTo(8);
	}

	@Nested
	@ActiveProfiles("local")
	class LocalProfile {

		@Autowired
		private BookingProperties bookingProperties;

		@Test
		void raisesTheLimit() {
			assertThat(bookingProperties.maxSeatsPerBooking()).isEqualTo(10);
		}

	}

	@Nested
	@ActiveProfiles("cloud")
	class CloudProfile {

		@Autowired
		private BookingProperties bookingProperties;

		@Test
		void lowersTheLimit() {
			assertThat(bookingProperties.maxSeatsPerBooking()).isEqualTo(6);
		}

	}

}
