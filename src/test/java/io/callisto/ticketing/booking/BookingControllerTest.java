package io.callisto.ticketing.booking;

import io.callisto.ticketing.booking.dto.BookingRequest;
import io.callisto.ticketing.catalog.EventRepository;
import io.callisto.ticketing.domain.BookingStatus;
import io.callisto.ticketing.domain.Event;
import io.callisto.ticketing.domain.Venue;
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

	private static final String EVENT_ID = "event-1";

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
		when(bookings.save(any(Booking.class)))
				.thenAnswer(invocation -> ((Booking) invocation.getArgument(0)).toBuilder().id("booking-1").build());

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
		Booking cancelled = Booking.builder().id("booking-1").eventId(EVENT_ID).customerName("Grace Hopper")
				.seatCount(1).status(new BookingStatus.Cancelled()).build();
		when(bookings.findById("booking-1")).thenReturn(Optional.of(cancelled));

		mockMvc.perform(post("/events/" + EVENT_ID + "/bookings/booking-1/cancel"))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectsFetchingABookingUnderTheWrongEvent() throws Exception {
		Booking booking = Booking.builder().id("booking-1").eventId(EVENT_ID).customerName("Hedy Lamarr")
				.seatCount(1).status(new BookingStatus.Confirmed()).build();
		when(bookings.findById("booking-1")).thenReturn(Optional.of(booking));

		mockMvc.perform(get("/events/other-event/bookings/booking-1"))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsBookingAnUnknownEvent() throws Exception {
		when(events.findById("does-not-exist")).thenReturn(Optional.empty());

		mockMvc.perform(post("/events/does-not-exist/bookings")
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
		Venue venue = Venue.builder().id("venue-1").name("Blue Room").capacity(120).build();
		return Event.builder().id(EVENT_ID).name("Jazz Night").venue(venue).startTime(Instant.now()).build();
	}

}
