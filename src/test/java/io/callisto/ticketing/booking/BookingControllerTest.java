package io.callisto.ticketing.booking;

import io.callisto.ticketing.booking.dto.BookingRequest;
import io.callisto.ticketing.catalog.Event;
import io.callisto.ticketing.catalog.EventRepository;
import io.callisto.ticketing.catalog.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer test: HTTP contract only — status codes, JSON shape, validation,
 * routing. {@link BookingRepository}, {@link EventRepository}, and
 * {@link BookingProperties} are all mocked; no database involved. For the full stack
 * see {@link io.callisto.ticketing.BookingJourneyIntegrationTest}.
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
	private BookingRepository bookings;

	@MockitoBean
	private EventRepository events;

	@MockitoBean
	private BookingProperties bookingProperties;

	@Test
	void createsABooking() throws Exception {
		when(events.findById(EVENT_ID)).thenReturn(Optional.of(stubEvent()));
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);
		when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
		Booking cancelled = Booking.builder().id(BOOKING_ID).eventId(EVENT_ID).customerName("Grace Hopper")
				.seatCount(1).status(BookingStatus.CANCELLED).build();
		when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(cancelled));

		mockMvc.perform(post("/events/" + EVENT_ID + "/bookings/" + BOOKING_ID + "/cancel"))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectsFetchingABookingUnderTheWrongEvent() throws Exception {
		Booking booking = Booking.builder().id(BOOKING_ID).eventId(EVENT_ID).customerName("Hedy Lamarr")
				.seatCount(1).status(BookingStatus.CONFIRMED).build();
		when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

		mockMvc.perform(get("/events/999/bookings/" + BOOKING_ID))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsBookingAnUnknownEvent() throws Exception {
		when(events.findById(999L)).thenReturn(Optional.empty());

		mockMvc.perform(post("/events/999/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new BookingRequest("Alan Turing", 1))))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsMoreSeatsThanAllowed() throws Exception {
		when(events.findById(EVENT_ID)).thenReturn(Optional.of(stubEvent()));
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);

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

	private static Event stubEvent() {
		Venue venue = Venue.builder().id(1L).name("Blue Room").capacity(120).build();
		return Event.builder().id(EVENT_ID).name("Jazz Night").venue(venue).startTime(Instant.now()).bookedSeats(0).build();
	}

}
