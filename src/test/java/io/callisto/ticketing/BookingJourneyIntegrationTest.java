package io.callisto.ticketing;

import io.callisto.ticketing.booking.dto.BookingRequest;
import io.callisto.ticketing.booking.dto.BookingResponse;
import io.callisto.ticketing.catalog.dto.EventRequest;
import io.callisto.ticketing.catalog.dto.EventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test: real HTTP requests through the whole application —
 * controller, repository, real Postgres (via {@link AbstractIntegrationTest}) —
 * proving the pieces genuinely work together, not just individually. Deliberately
 * kept to one broad happy-path journey rather than every edge case; those are the
 * controller and persistence layers' job. This is the expensive top of the testing
 * pyramid, used sparingly on purpose.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class BookingJourneyIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void createsBooksFetchesAndCancelsAcrossTheWholeStack() {
		EventRequest eventRequest = new EventRequest("Jazz Night", "Blue Room", 120, Instant.now().plus(30, ChronoUnit.DAYS));
		ResponseEntity<EventResponse> eventCreated = rest.postForEntity("/events", eventRequest, EventResponse.class);
		assertThat(eventCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Long eventId = eventCreated.getBody().id();

		BookingRequest bookingRequest = new BookingRequest("Ada Lovelace", 2);
		ResponseEntity<BookingResponse> bookingCreated = rest.postForEntity(
				"/events/" + eventId + "/bookings", bookingRequest, BookingResponse.class);
		assertThat(bookingCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(bookingCreated.getBody().status()).isEqualTo("CONFIRMED");
		String bookingId = bookingCreated.getBody().id();

		ResponseEntity<BookingResponse> fetched = rest.getForEntity(
				"/events/" + eventId + "/bookings/" + bookingId, BookingResponse.class);
		assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(fetched.getBody().seatCount()).isEqualTo(2);

		ResponseEntity<BookingResponse> cancelled = rest.postForEntity(
				"/events/" + eventId + "/bookings/" + bookingId + "/cancel", null, BookingResponse.class);
		assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cancelled.getBody().status()).isEqualTo("CANCELLED");
	}

}
