package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventController;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.dto.EventResponse;
import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewResponse;
import io.callisto.ticketing.review.dto.ReviewSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test: {@link ReviewService}'s business rules — the event-name lookup, and the
 * not-found rule it inherits from {@link EventController} — with
 * {@link ReviewRepository} and {@link EventController} both mocked, no Spring context
 * or database. Depending on {@link EventController} here, not a repository, is the
 * session 4 planted Modulith violation — see {@link ReviewService}'s own comment.
 * For persistence and the real aggregation behavior see {@link ReviewRepositoryTest}.
 */
class ReviewServiceTest {

	private static final Long EVENT_ID = 1L;

	private final ReviewRepository reviews = mock(ReviewRepository.class);
	private final EventController events = mock(EventController.class);
	private final ReviewService service = new ReviewService(reviews, events);

	@Test
	void createsAReviewAndFillsInTheEventName() {
		when(events.get(EVENT_ID)).thenReturn(stubEvent());
		when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReviewResponse created = service.create(EVENT_ID, new ReviewRequest(5, "Loved it"));

		assertThat(created.rating()).isEqualTo(5);
		assertThat(created.eventName()).isEqualTo("Jazz Night");
	}

	@Test
	void rejectsAReviewForAnUnknownEvent() {
		when(events.get(999L)).thenThrow(new EventNotFoundException(999L));

		assertThatThrownBy(() -> service.create(999L, new ReviewRequest(5, "Loved it")))
				.isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void listsReviewsWithTheEventNameAttached() {
		when(events.get(EVENT_ID)).thenReturn(stubEvent());
		Review review = Review.builder().id("review-1").eventId(EVENT_ID).rating(4).comment("Good show").build();
		when(reviews.findByEventId(EVENT_ID)).thenReturn(List.of(review));

		List<ReviewResponse> listed = service.list(EVENT_ID);

		assertThat(listed).hasSize(1);
		assertThat(listed.getFirst().eventName()).isEqualTo("Jazz Night");
	}

	@Test
	void summaryFallsBackToZeroWhenThereAreNoReviews() {
		when(reviews.summarizeByEventId(EVENT_ID)).thenReturn(Optional.empty());

		ReviewSummary summary = service.summary(EVENT_ID);

		assertThat(summary.averageRating()).isEqualTo(0.0);
		assertThat(summary.totalReviews()).isEqualTo(0);
	}

	private static EventResponse stubEvent() {
		return new EventResponse(EVENT_ID, "Jazz Night", "Blue Room", 120, Instant.now());
	}

}
