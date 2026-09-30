package io.callisto.ticketing.review;

import io.callisto.ticketing.AbstractIntegrationTest;
import io.callisto.ticketing.review.dto.ReviewSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Persistence-layer test: verifies {@link Review}'s document mapping and the
 * {@code @Aggregation} pipeline against a real MongoDB instance (via
 * {@link AbstractIntegrationTest}). No web layer involved. For the HTTP contract see
 * {@link ReviewControllerTest}.
 */
@DataMongoTest
class ReviewRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private ReviewRepository reviews;

	@Test
	void savesAReviewAndGeneratesItsOwnId() {
		Review review = Review.builder().eventId(newEventId()).rating(5).comment("Loved it").build();

		Review saved = reviews.save(review);

		assertThat(saved.getId()).isNotBlank();
	}

	@Test
	void findsReviewsByEventId() {
		long eventId = newEventId();
		reviews.save(Review.builder().eventId(eventId).rating(5).comment("Great").build());
		reviews.save(Review.builder().eventId(eventId).rating(3).comment("Okay").build());
		reviews.save(Review.builder().eventId(newEventId()).rating(1).comment("Different event").build());

		List<Review> found = reviews.findByEventId(eventId);

		assertThat(found).hasSize(2).extracting(Review::getRating).containsExactlyInAnyOrder(5, 3);
	}

	@Test
	void aggregatesTheAverageRatingAndCountForAnEvent() {
		long eventId = newEventId();
		reviews.save(Review.builder().eventId(eventId).rating(5).comment("Great").build());
		reviews.save(Review.builder().eventId(eventId).rating(2).comment("Meh").build());

		Optional<ReviewSummary> summary = reviews.summarizeByEventId(eventId);

		assertThat(summary).isPresent();
		assertThat(summary.get().averageRating()).isCloseTo(3.5, within(0.001));
		assertThat(summary.get().totalReviews()).isEqualTo(2);
	}

	@Test
	void returnsEmptyForAnEventWithNoReviews() {
		assertThat(reviews.summarizeByEventId(newEventId())).isEmpty();
	}

	// Reviews live in a shared Mongo container with no automatic rollback between
	// tests, so eventId values need to be distinct per test to avoid cross-test
	// collisions — same reasoning the old random-UUID-eventId version had, just
	// producing a Long now that Event's real id is one.
	private static long newEventId() {
		return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
	}

}
