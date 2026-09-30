package io.callisto.ticketing.booking.adapter.in.web;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.booking.adapter.in.web.dto.BookingRequest;
import io.callisto.ticketing.booking.application.BookingService;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingId;
import io.callisto.ticketing.booking.domain.BookingStatus;
import io.callisto.ticketing.catalog.EventNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer test: HTTP contract only — status codes, JSON shape, request
 * validation, routing. {@link BookingService} is mocked, so this deliberately can't
 * prove any business rule (too-many-seats, cancel-twice, wrong-event scoping) — only
 * that the controller calls the service and shapes the response correctly. Those
 * rules are {@link BookingServiceTest}'s job. For the full stack see
 * {@link io.callisto.ticketing.BookingJourneyIntegrationTest}.
 */
@WebMvcTest(BookingController.class)
class BookingControllerTest {

	private static final Long EVENT_ID = 1L;
	private static final Long BOOKING_ID = 1L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private BookingService bookings;

	@Test
	void createsABooking() throws Exception {
		Booking saved = stubBooking(BookingStatus.CONFIRMED);
		when(bookings.create(any(Booking.class))).thenReturn(saved);

		mockMvc.perform(post("/events/" + EVENT_ID + "/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new BookingRequest("Ada Lovelace", 2))))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.seatCount").value(2));
	}

	@Test
	void rejectsCancellingAnAlreadyCancelledBooking() throws Exception {
		when(bookings.cancel(EVENT_ID, BookingId.of(BOOKING_ID))).thenThrow(new BookingAlreadyCancelledException(BOOKING_ID));

		mockMvc.perform(post("/events/" + EVENT_ID + "/bookings/" + BOOKING_ID + "/cancel"))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectsFetchingABookingUnderTheWrongEvent() throws Exception {
		when(bookings.get(999L, BookingId.of(BOOKING_ID))).thenThrow(new BookingNotFoundException(BOOKING_ID));

		mockMvc.perform(get("/events/999/bookings/" + BOOKING_ID))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsBookingAnUnknownEvent() throws Exception {
		when(bookings.create(any(Booking.class))).thenThrow(new EventNotFoundException(999L));

		mockMvc.perform(post("/events/999/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new BookingRequest("Alan Turing", 1))))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsMoreSeatsThanAllowed() throws Exception {
		when(bookings.create(any(Booking.class)))
				.thenThrow(new TooManySeatsRequestedException(50, 8));

		mockMvc.perform(post("/events/" + EVENT_ID + "/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new BookingRequest("Margaret Hamilton", 50))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsAnInvalidBookingRequest() throws Exception {
		mockMvc.perform(post("/events/" + EVENT_ID + "/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new BookingRequest(" ", 1))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors").exists());
	}

	private static Booking stubBooking(BookingStatus status) {
		return Booking.builder().id(BOOKING_ID).eventId(EVENT_ID).customerName("Ada Lovelace")
				.seatCount(2).status(status).build();
	}

}
