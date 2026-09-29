package io.callisto.ticketing.booking.application;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// Scoped to just property binding via classes=..., not the whole app — no JPA/web/Mongo,
// so no database needed. Still goes through real SpringApplication profile-file loading,
// unlike ApplicationContextRunner, which is what this test actually needs to verify.
@SpringBootTest(classes = BookingPropertiesProfileTest.TestConfig.class)
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

	@Configuration
	@EnableConfigurationProperties(BookingProperties.class)
	static class TestConfig {
	}

}
