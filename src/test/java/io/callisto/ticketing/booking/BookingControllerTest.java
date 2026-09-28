package io.callisto.ticketing.booking;

import io.callisto.ticketing.booking.dto.BookingRequest;
import io.callisto.ticketing.booking.dto.BookingResponse;
import io.callisto.ticketing.catalog.dto.EventRequest;
import io.callisto.ticketing.catalog.dto.EventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class BookingControllerTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void createsFetchesAndCancelsABooking() {
		String eventId = createEvent();
		BookingRequest request = new BookingRequest("Ada Lovelace", 2);

		ResponseEntity<BookingResponse> created = rest.postForEntity("/events/" + eventId + "/bookings", request, BookingResponse.class);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getHeaders().getLocation()).isNotNull();
		assertThat(created.getBody().status()).isEqualTo("CONFIRMED");
		String bookingId = created.getBody().id();

		ResponseEntity<BookingResponse> fetched = rest.getForEntity("/events/" + eventId + "/bookings/" + bookingId, BookingResponse.class);
		assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(fetched.getBody().seatCount()).isEqualTo(2);

		ResponseEntity<BookingResponse> cancelled = rest.postForEntity(
				"/events/" + eventId + "/bookings/" + bookingId + "/cancel", null, BookingResponse.class);
		assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cancelled.getBody().status()).isEqualTo("CANCELLED");
	}

	@Test
	void rejectsCancellingTwice() {
		String eventId = createEvent();
		String bookingId = createBooking(eventId, "Grace Hopper", 1).id();

		rest.postForEntity("/events/" + eventId + "/bookings/" + bookingId + "/cancel", null, BookingResponse.class);
		ResponseEntity<ProblemDetail> secondCancel = rest.postForEntity(
				"/events/" + eventId + "/bookings/" + bookingId + "/cancel", null, ProblemDetail.class);

		assertThat(secondCancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void rejectsFetchingOrCancellingABookingUnderTheWrongEvent() {
		String ownerEventId = createEvent();
		String otherEventId = createEvent();
		String bookingId = createBooking(ownerEventId, "Hedy Lamarr", 1).id();

		ResponseEntity<ProblemDetail> fetchUnderWrongEvent = rest.getForEntity(
				"/events/" + otherEventId + "/bookings/" + bookingId, ProblemDetail.class);
		assertThat(fetchUnderWrongEvent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<ProblemDetail> cancelUnderWrongEvent = rest.postForEntity(
				"/events/" + otherEventId + "/bookings/" + bookingId + "/cancel", null, ProblemDetail.class);
		assertThat(cancelUnderWrongEvent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectsBookingAnUnknownEvent() {
		ResponseEntity<ProblemDetail> response = rest.postForEntity(
				"/events/does-not-exist/bookings", new BookingRequest("Alan Turing", 1), ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectsMoreSeatsThanAllowed() {
		String eventId = createEvent();

		ResponseEntity<ProblemDetail> response = rest.postForEntity(
				"/events/" + eventId + "/bookings", new BookingRequest("Margaret Hamilton", 50), ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsAnInvalidBookingRequest() {
		String eventId = createEvent();

		ResponseEntity<ProblemDetail> response = rest.postForEntity(
				"/events/" + eventId + "/bookings", new BookingRequest(" ", 1), ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getProperties()).containsKey("errors");
	}

	private String createEvent() {
		EventRequest request = new EventRequest("Jazz Night", "Blue Room", 120, Instant.now().plus(30, ChronoUnit.DAYS));
		return rest.postForEntity("/events", request, EventResponse.class).getBody().id();
	}

	private BookingResponse createBooking(String eventId, String customerName, int seatCount) {
		return rest.postForEntity("/events/" + eventId + "/bookings", new BookingRequest(customerName, seatCount), BookingResponse.class).getBody();
	}

}
