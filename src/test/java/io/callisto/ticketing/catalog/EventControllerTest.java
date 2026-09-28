package io.callisto.ticketing.catalog;

import io.callisto.ticketing.catalog.dto.EventRequest;
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
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer test: HTTP contract only — status codes, JSON shape, validation,
 * routing. {@link EventRepository} is mocked; no database involved. For a real
 * end-to-end flow see {@link io.callisto.ticketing.EventJourneyIntegrationTest}.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private EventRepository events;

	@Test
	void createsAnEvent() throws Exception {
		EventRequest request = new EventRequest("Jazz Night", "Blue Room", 120, Instant.now().plus(30, ChronoUnit.DAYS));
		when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(request)))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Jazz Night"))
				.andExpect(jsonPath("$.venueCapacity").value(120));
	}

	@Test
	void rejectsAnInvalidEvent() throws Exception {
		EventRequest blankName = new EventRequest(" ", "Blue Room", 120, Instant.now().plus(30, ChronoUnit.DAYS));

		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(blankName)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsNotFoundForAnUnknownEvent() throws Exception {
		when(events.findById("does-not-exist")).thenReturn(Optional.empty());

		mockMvc.perform(get("/events/does-not-exist"))
				.andExpect(status().isNotFound());
	}

	@Test
	void fetchesAnExistingEvent() throws Exception {
		Venue venue = Venue.builder().id("venue-1").name("Attic").capacity(60).build();
		Event event = Event.builder().id("event-1").name("Comedy Set").venue(venue)
				.startTime(Instant.now().plus(10, ChronoUnit.DAYS)).build();
		when(events.findById("event-1")).thenReturn(Optional.of(event));

		mockMvc.perform(get("/events/event-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Comedy Set"))
				.andExpect(jsonPath("$.venueCapacity").value(60));
	}

	@Test
	void deletesAnEvent() throws Exception {
		Venue venue = Venue.builder().id("venue-1").name("Attic").capacity(60).build();
		Event event = Event.builder().id("event-1").name("Comedy Set").venue(venue).startTime(Instant.now()).build();
		when(events.findById("event-1")).thenReturn(Optional.of(event));

		mockMvc.perform(delete("/events/event-1"))
				.andExpect(status().isNoContent());
	}

}
