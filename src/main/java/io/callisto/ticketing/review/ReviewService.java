package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventClient;
import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewResponse;
import io.callisto.ticketing.review.dto.ReviewSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Package-private — ReviewController is the only caller within this module.
//
// Returns ReviewResponse directly for create()/list(), not Review — the one
// deliberate exception to "service returns domain objects, controller maps them"
// (see EventService/BookingService). Both operations need the event's name from
// EventClient for validation (does the event exist at all) *and* for the response
// shape, and EventClient is meant to behave like a real remote call — calling it
// once per operation and reusing the result, rather than twice (once here, once in
// the controller), is the point.
@Service
@RequiredArgsConstructor
class ReviewService {

	private final ReviewRepository reviews;
	// Not EventRepository, and not EventController — EventClient is catalog's
	// purpose-built API for exactly this: a narrow, read-only lookup, shaped the way
	// a real client call would be. See EventClient's own comment for why. This is
	// the fix for session 4's planted Modulith violation — this class used to depend
	// on EventController and reach for EventResponse, a catalog-internal type.
	private final EventClient events;

	ReviewResponse create(Long eventId, ReviewRequest request) {
		String eventName = events.nameOf(eventId); // throws EventNotFoundException if missing
		Review saved = reviews.save(ReviewMapper.toNewReview(eventId, request));
		return ReviewMapper.toResponse(saved, eventName);
	}

	List<ReviewResponse> list(Long eventId) {
		String eventName = events.nameOf(eventId);
		return reviews.findByEventId(eventId).stream()
				.map(review -> ReviewMapper.toResponse(review, eventName))
				.toList();
	}

	ReviewSummary summary(Long eventId) {
		return reviews.summarizeByEventId(eventId).orElse(new ReviewSummary(0.0, 0));
	}

}
