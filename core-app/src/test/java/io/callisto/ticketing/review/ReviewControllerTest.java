package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewResponse;
import io.callisto.ticketing.review.dto.ReviewSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer test: HTTP contract only — status codes, JSON shape, request
 * validation, routing. {@link ReviewService} is mocked, so this deliberately can't
 * prove the event-name lookup or the not-found rule actually work — only that the
 * controller calls the service and returns what it's given. Those rules are
 * {@link ReviewServiceTest}'s job. For persistence and the aggregation behavior see
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
	private ReviewService reviews;

	@Test
	void createsAReview() throws Exception {
		when(reviews.create(eq(EVENT_ID), any(ReviewRequest.class)))
				.thenReturn(new ReviewResponse("review-1", EVENT_ID, 5, "Loved it", "Jazz Night"));

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
		when(reviews.create(eq(999L), any(ReviewRequest.class)))
				.thenThrow(new EventNotFoundException(999L));

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
		when(reviews.list(EVENT_ID)).thenReturn(List.of(new ReviewResponse("review-1", EVENT_ID, 4, "Good show", "Jazz Night")));

		mockMvc.perform(get("/events/" + EVENT_ID + "/reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].rating").value(4))
				.andExpect(jsonPath("$[0].eventName").value("Jazz Night"));
	}

	@Test
	void returnsTheAverageRatingSummary() throws Exception {
		when(reviews.summary(EVENT_ID)).thenReturn(new ReviewSummary(4.5, 2));

		mockMvc.perform(get("/events/" + EVENT_ID + "/reviews/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.averageRating").value(4.5))
				.andExpect(jsonPath("$.totalReviews").value(2));
	}

	@Test
	void returnsAZeroSummaryWhenThereAreNoReviewsYet() throws Exception {
		when(reviews.summary(EVENT_ID)).thenReturn(new ReviewSummary(0.0, 0));

		mockMvc.perform(get("/events/" + EVENT_ID + "/reviews/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.averageRating").value(0.0))
				.andExpect(jsonPath("$.totalReviews").value(0));
	}

}
