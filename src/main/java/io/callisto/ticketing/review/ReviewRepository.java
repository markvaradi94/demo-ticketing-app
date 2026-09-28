package io.callisto.ticketing.review;

import io.callisto.ticketing.review.dto.ReviewSummary;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {

	List<Review> findByEventId(Long eventId);

	@Aggregation(pipeline = {
			"{ '$match': { 'eventId': ?0 } }",
			"{ '$group': { '_id': '$eventId', 'averageRating': { '$avg': '$rating' }, 'totalReviews': { '$sum': 1 } } }"
	})
	Optional<ReviewSummary> summarizeByEventId(Long eventId);

}
