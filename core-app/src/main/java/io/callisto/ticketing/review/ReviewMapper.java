package io.callisto.ticketing.review;

import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewResponse;

import java.util.UUID;

// Package-private — internal to this module's own controller and service.
final class ReviewMapper {

	private ReviewMapper() {
	}

	static Review toNewReview(Long eventId, ReviewRequest request) {
		// We assign the id ourselves here, unlike Event/Booking — MongoDB would
		// generate its own ObjectId-backed string automatically if left null, but a
		// self-assigned UUID is the more realistic choice for a document store: it
		// doesn't depend on the driver's id scheme, so it stays stable if this
		// document ever moves to another store or gets referenced from outside Mongo.
		return Review.builder().id(UUID.randomUUID().toString())
				.eventId(eventId).rating(request.rating()).comment(request.comment()).build();
	}

	static ReviewResponse toResponse(Review review, String eventName) {
		return new ReviewResponse(review.getId(), review.getEventId(), review.getRating(), review.getComment(), eventName);
	}

}
