package io.callisto.ticketing;

import io.callisto.ticketing.booking.adapter.in.web.dto.BookingRequest;
import io.callisto.ticketing.booking.adapter.in.web.dto.BookingResponse;
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
		Long bookingId = bookingCreated.getBody().id();

		ResponseEntity<BookingResponse> fetched = rest.getForEntity(
				"/events/" + eventId + "/bookings/" + bookingId, BookingResponse.class);
		assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(fetched.getBody().seatCount()).isEqualTo(2);

		ResponseEntity<BookingResponse> cancelled = rest.postForEntity(
				"/events/" + eventId + "/bookings/" + bookingId + "/cancel", null, BookingResponse.class);
		assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cancelled.getBody().status()).isEqualTo("CANCELLED");

		// Real HTTP against the shared container, same as EventJourneyIntegrationTest —
		// clean up what this test created rather than leaving it for the next test.
		rest.exchange("/events/" + eventId, HttpMethod.DELETE, null, Void.class);
	}

	@Test
	void rejectsABookingThatWouldExceedCapacity() {
		EventRequest eventRequest = new EventRequest("Tiny Room Gig", "Back Room", 3, Instant.now().plus(10, ChronoUnit.DAYS));
		ResponseEntity<EventResponse> eventCreated = rest.postForEntity("/events", eventRequest, EventResponse.class);
		Long eventId = eventCreated.getBody().id();

		ResponseEntity<BookingResponse> firstBooking = rest.postForEntity(
				"/events/" + eventId + "/bookings", new BookingRequest("Ada Lovelace", 3), BookingResponse.class);
		assertThat(firstBooking.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> overbooked = rest.postForEntity(
				"/events/" + eventId + "/bookings", new BookingRequest("Alan Turing", 1), String.class);
		assertThat(overbooked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		rest.exchange("/events/" + eventId, HttpMethod.DELETE, null, Void.class);
	}

	@Test
	void cancellingABookingReleasesItsSeatsForReuse() {
		EventRequest eventRequest = new EventRequest("Small Room Gig", "Back Room", 2, Instant.now().plus(10, ChronoUnit.DAYS));
		ResponseEntity<EventResponse> eventCreated = rest.postForEntity("/events", eventRequest, EventResponse.class);
		Long eventId = eventCreated.getBody().id();

		ResponseEntity<BookingResponse> booked = rest.postForEntity(
				"/events/" + eventId + "/bookings", new BookingRequest("Ada Lovelace", 2), BookingResponse.class);
		assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Long bookingId = booked.getBody().id();

		// At capacity — a second booking is rejected until the first is cancelled.
		ResponseEntity<String> rejectedWhileFull = rest.postForEntity(
				"/events/" + eventId + "/bookings", new BookingRequest("Alan Turing", 1), String.class);
		assertThat(rejectedWhileFull.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		rest.postForEntity("/events/" + eventId + "/bookings/" + bookingId + "/cancel", null, BookingResponse.class);

		// Cancelling published BookingCancelled, the listener released the seats
		// synchronously — the same booking that just failed now succeeds.
		ResponseEntity<BookingResponse> rebooked = rest.postForEntity(
				"/events/" + eventId + "/bookings", new BookingRequest("Alan Turing", 1), BookingResponse.class);
		assertThat(rebooked.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		rest.exchange("/events/" + eventId, HttpMethod.DELETE, null, Void.class);
	}

}
