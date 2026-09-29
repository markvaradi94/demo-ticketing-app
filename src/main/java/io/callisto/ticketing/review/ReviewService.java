package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventController;
import io.callisto.ticketing.catalog.dto.EventResponse;
import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewResponse;
import io.callisto.ticketing.review.dto.ReviewSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Package-private — ReviewController is the only caller within this module.
//
// PLANTED MODULITH VIOLATION (session 4's lab task to find and fix) — this calls
// catalog's own controller directly, purely to reuse its existing not-found check
// and reach the event's name, instead of going through EventRepository/a proper
// module-facing API the way BookingService does. It compiles and runs fine; it's
// Spring Modulith's verify() that catches it, because EventController.get()'s return
// type, EventResponse, lives in catalog.dto — a nested package, not catalog's root —
// so it's catalog-internal, not catalog's public API. Controller-to-controller
// coupling like this is also a real-world smell on its own, independent of Modulith:
// reach for another module's repository (or a purpose-built client), not its
// controller.
@Service
@RequiredArgsConstructor
class ReviewService {

	private final ReviewRepository reviews;
	private final EventController events;

	ReviewResponse create(Long eventId, ReviewRequest request) {
		EventResponse event = events.get(eventId); // throws EventNotFoundException if missing
		Review saved = reviews.save(ReviewMapper.toNewReview(eventId, request));
		return ReviewMapper.toResponse(saved, event.name());
	}

	List<ReviewResponse> list(Long eventId) {
		EventResponse event = events.get(eventId);
		return reviews.findByEventId(eventId).stream()
				.map(review -> ReviewMapper.toResponse(review, event.name()))
				.toList();
	}

	ReviewSummary summary(Long eventId) {
		return reviews.summarizeByEventId(eventId).orElse(new ReviewSummary(0.0, 0));
	}

}
