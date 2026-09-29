package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventClient;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewResponse;
import io.callisto.ticketing.review.dto.ReviewSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test: {@link ReviewService}'s business rules — the event-name lookup (and the
 * not-found it can throw) folded into a single {@link EventClient} call per
 * operation — with {@link ReviewRepository} and {@link EventClient} both mocked, no
 * Spring context or database. This is the layer that used to be tested inside
 * {@link ReviewControllerTest} before the service existed. For persistence and the
 * real aggregation behavior see {@link ReviewRepositoryTest}.
 */
class ReviewServiceTest {

	private static final Long EVENT_ID = 1L;

	private final ReviewRepository reviews = mock(ReviewRepository.class);
	private final EventClient events = mock(EventClient.class);
	private final ReviewService service = new ReviewService(reviews, events);

	@Test
	void createsAReviewAndFillsInTheEventName() {
		when(events.nameOf(EVENT_ID)).thenReturn("Jazz Night");
		when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReviewResponse created = service.create(EVENT_ID, new ReviewRequest(5, "Loved it"));

		assertThat(created.rating()).isEqualTo(5);
		assertThat(created.eventName()).isEqualTo("Jazz Night");
	}

	@Test
	void rejectsAReviewForAnUnknownEvent() {
		when(events.nameOf(999L)).thenThrow(new EventNotFoundException(999L));

		assertThatThrownBy(() -> service.create(999L, new ReviewRequest(5, "Loved it")))
				.isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void listsReviewsWithTheEventNameAttached() {
		when(events.nameOf(EVENT_ID)).thenReturn("Jazz Night");
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

}
