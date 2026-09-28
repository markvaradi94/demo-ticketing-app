package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventRepository;
import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

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
 * routing. {@link ReviewRepository} and {@link EventRepository} are both mocked; no
 * database involved. For persistence and the aggregation behavior see
 * {@link ReviewRepositoryTest}.
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
	private EventRepository events;

	@Test
	void createsAReview() throws Exception {
		when(events.existsById(EVENT_ID)).thenReturn(true);
		when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/events/" + EVENT_ID + "/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new ReviewRequest(5, "Loved it"))))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.rating").value(5))
				.andExpect(jsonPath("$.comment").value("Loved it"));
	}

	@Test
	void rejectsAReviewForAnUnknownEvent() throws Exception {
		when(events.existsById(999L)).thenReturn(false);

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
		Review review = Review.builder().id("review-1").eventId(EVENT_ID).rating(4).comment("Good show").build();
		when(reviews.findByEventId(EVENT_ID)).thenReturn(List.of(review));

		mockMvc.perform(get("/events/" + EVENT_ID + "/reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].rating").value(4));
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

}
