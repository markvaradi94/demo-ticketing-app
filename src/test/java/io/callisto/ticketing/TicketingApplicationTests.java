package io.callisto.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: does the whole Spring context boot with real infrastructure wired in
 * (JPA, MongoDB, docker-compose properties)? Deliberately trivial — no assertions
 * beyond "it started." For a real end-to-end scenario, see
 * {@link BookingJourneyIntegrationTest}.
 */
@SpringBootTest
class TicketingApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
