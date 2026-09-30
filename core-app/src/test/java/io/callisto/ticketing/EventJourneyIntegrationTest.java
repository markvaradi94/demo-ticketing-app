package io.callisto.ticketing;

import io.callisto.ticketing.catalog.dto.EventRequest;
import io.callisto.ticketing.catalog.dto.EventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test: real HTTP requests through the whole application —
 * controller, repository, real Postgres (via {@link AbstractIntegrationTest}) —
 * proving the pieces genuinely work together, not just individually. Deliberately
 * one broad journey covering the full Event CRUD lifecycle, not every edge case
 * (those are {@link io.callisto.ticketing.catalog.EventControllerTest}'s job) —
 * distinct from {@link BookingJourneyIntegrationTest}, which covers event+booking
 * interaction rather than Event CRUD completeness.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EventJourneyIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void createsListsUpdatesAndDeletesAnEventAcrossTheWholeStack() {
		EventRequest request = new EventRequest("Jazz Night", "Blue Room", 120, new BigDecimal("25.00"), Instant.now().plus(30, ChronoUnit.DAYS));

		ResponseEntity<EventResponse> created = rest.postForEntity("/events", request, EventResponse.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getHeaders().getLocation()).isNotNull();
		Long id = created.getBody().id();

		ResponseEntity<EventResponse[]> listed = rest.getForEntity("/events", EventResponse[].class);
		assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(listed.getBody()).extracting(EventResponse::id).contains(id);

		EventRequest update = new EventRequest("Jazz Night (Rescheduled)", "Blue Room", 150, new BigDecimal("30.00"), Instant.now().plus(45, ChronoUnit.DAYS));
		rest.put("/events/" + id, update);
		ResponseEntity<EventResponse> fetched = rest.getForEntity("/events/" + id, EventResponse.class);
		assertThat(fetched.getBody().name()).isEqualTo("Jazz Night (Rescheduled)");
		assertThat(fetched.getBody().venueCapacity()).isEqualTo(150);

		ResponseEntity<Void> deleted = rest.exchange("/events/" + id, HttpMethod.DELETE, null, Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(rest.getForEntity("/events/" + id, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
