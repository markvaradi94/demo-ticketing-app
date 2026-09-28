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
 * both controllers and both repositories together — proving the pieces genuinely
 * work together, not just individually. Backed by the in-memory repositories for
 * now; nothing about this test's shape changes once session 3 swaps in a real
 * Postgres-backed repository — that's the point of testing through the repository's
 * public contract rather than its implementation. Deliberately one broad happy-path
 * journey rather than every edge case; those are {@link
 * io.callisto.ticketing.catalog.EventControllerTest} and {@link
 * io.callisto.ticketing.booking.BookingControllerTest}'s job.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class BookingJourneyIntegrationTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void createsBooksFetchesAndCancelsAcrossTheWholeStack() {
		EventRequest eventRequest = new EventRequest("Jazz Night", "Blue Room", 120, Instant.now().plus(30, ChronoUnit.DAYS));
		ResponseEntity<EventResponse> eventCreated = rest.postForEntity("/events", eventRequest, EventResponse.class);
		assertThat(eventCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String eventId = eventCreated.getBody().id();

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
