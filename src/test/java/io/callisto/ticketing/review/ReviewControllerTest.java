package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventController;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.dto.EventResponse;
import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
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
 * routing. {@link ReviewRepository} and {@link EventController} are both mocked; no
 * database involved. For persistence and the aggregation behavior see
 * {@link ReviewRepositoryTest}.
 *
 * <p>{@link EventController} is mocked here purely because that's what
 * {@code ReviewController} is (deliberately, wrongly) wired to — see the comment on
 * that field for why this is the session 4 planted Modulith violation, not a pattern
 * to copy for other controllers.
 */
@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

	private static final Long EVENT_ID = 1L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ReviewRepository reviews;

	@MockitoBean
	private EventController events;

	@Test
	void createsAReview() throws Exception {
		when(events.get(EVENT_ID)).thenReturn(stubEvent());
		when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/events/" + EVENT_ID + "/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new ReviewRequest(5, "Loved it"))))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.rating").value(5))
				.andExpect(jsonPath("$.comment").value("Loved it"))
				.andExpect(jsonPath("$.eventName").value("Jazz Night"));
	}

	@Test
	void rejectsAReviewForAnUnknownEvent() throws Exception {
		when(events.get(999L)).thenThrow(new EventNotFoundException(999L));

		mockMvc.perform(post("/events/999/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new ReviewRequest(5, "Loved it"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsAnInvalidRating() throws Exception {
		mockMvc.perform(post("/events/" + EVENT_ID + "/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new ReviewRequest(9, "Too many stars"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listsReviewsForAnEvent() throws Exception {
		when(events.get(EVENT_ID)).thenReturn(stubEvent());
		Review review = Review.builder().id("review-1").eventId(EVENT_ID).rating(4).comment("Good show").build();
		when(reviews.findByEventId(EVENT_ID)).thenReturn(List.of(review));

		mockMvc.perform(get("/events/" + EVENT_ID + "/reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].rating").value(4))
				.andExpect(jsonPath("$[0].eventName").value("Jazz Night"));
	}

	@Test
	void returnsTheAverageRatingSummary() throws Exception {
		when(reviews.summarizeByEventId(EVENT_ID)).thenReturn(Optional.of(new ReviewSummary(4.5, 2)));

		mockMvc.perform(get("/events/" + EVENT_ID + "/reviews/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.averageRating").value(4.5))
				.andExpect(jsonPath("$.totalReviews").value(2));
	}

	@Test
	void returnsAZeroSummaryWhenThereAreNoReviewsYet() throws Exception {
		when(reviews.summarizeByEventId(EVENT_ID)).thenReturn(Optional.empty());

		mockMvc.perform(get("/events/" + EVENT_ID + "/reviews/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.averageRating").value(0.0))
				.andExpect(jsonPath("$.totalReviews").value(0));
	}

	private static EventResponse stubEvent() {
		return new EventResponse(EVENT_ID, "Jazz Night", "Blue Room", 120, Instant.now());
	}

}
