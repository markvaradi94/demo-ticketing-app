package io.callisto.ticketing.catalog;

import io.callisto.ticketing.catalog.dto.EventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer test: HTTP contract only — status codes, JSON shape, request
 * validation, routing. {@link EventService} is mocked, so this deliberately can't
 * prove any business rule actually works (not-found, the update-preserves-version
 * fix) — only that the controller calls the service and shapes the response
 * correctly. Those rules are {@link EventServiceTest}'s job now; before the service
 * layer existed, this class carried both concerns. For persistence behavior see
 * {@link EventRepositoryTest}, for full-stack flows see
 * {@link io.callisto.ticketing.EventJourneyIntegrationTest} and
 * {@link io.callisto.ticketing.BookingJourneyIntegrationTest}.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private EventService events;

	@Test
	void createsAnEvent() throws Exception {
		EventRequest request = new EventRequest("Jazz Night", "Blue Room", 120, new BigDecimal("25.00"), Instant.now().plus(30, ChronoUnit.DAYS));
		when(events.create(any(EventRequest.class))).thenReturn(stubEvent());

		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(request)))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Comedy Set"))
				.andExpect(jsonPath("$.venueCapacity").value(60));
	}

	@Test
	void rejectsAnInvalidEvent() throws Exception {
		EventRequest blankName = new EventRequest(" ", "Blue Room", 120, new BigDecimal("25.00"), Instant.now().plus(30, ChronoUnit.DAYS));

		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(blankName)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsNotFoundForAnUnknownEvent() throws Exception {
		when(events.get(999L)).thenThrow(new EventNotFoundException(999L));

		mockMvc.perform(get("/events/999"))
				.andExpect(status().isNotFound());
	}

	@Test
	void fetchesAnExistingEvent() throws Exception {
		when(events.get(1L)).thenReturn(stubEvent());

		mockMvc.perform(get("/events/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Comedy Set"))
				.andExpect(jsonPath("$.venueCapacity").value(60));
	}

	@Test
	void deletesAnEvent() throws Exception {
		mockMvc.perform(delete("/events/1"))
				.andExpect(status().isNoContent());
	}

	private static Event stubEvent() {
		Venue venue = Venue.builder().id(1L).name("Attic").capacity(60).build();
		return Event.builder().id(1L).name("Comedy Set").venue(venue)
				.startTime(Instant.now().plus(10, ChronoUnit.DAYS)).build();
	}

}
