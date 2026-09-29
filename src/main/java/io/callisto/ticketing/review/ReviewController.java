package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventController;
import io.callisto.ticketing.catalog.dto.EventResponse;
import io.callisto.ticketing.review.dto.ReviewRequest;
import io.callisto.ticketing.review.dto.ReviewResponse;
import io.callisto.ticketing.review.dto.ReviewSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewRepository reviews;
	// PLANTED MODULITH VIOLATION (session 4's lab task to find and fix) — this calls
	// catalog's own controller directly, purely to reuse its existing not-found check
	// and reach the event's name, instead of going through EventRepository the way
	// BookingController does. It compiles and runs fine; it's Spring Modulith's
	// verify() that catches it, because EventController.get()'s return type,
	// EventResponse, lives in catalog.dto — a nested package, not catalog's root — so
	// it's catalog-internal, not catalog's public API. Controller-to-controller
	// coupling like this is also a real-world smell on its own, independent of
	// Modulith: reach for another module's repository, not its controller.
	private final EventController events;

	@PostMapping
	public ResponseEntity<ReviewResponse> create(@PathVariable Long eventId, @Valid @RequestBody ReviewRequest request) {
		EventResponse event = events.get(eventId); // throws EventNotFoundException if missing

		// We assign the id ourselves here, unlike Event/Booking — MongoDB would
		// generate its own ObjectId-backed string automatically if left null, but a
		// self-assigned UUID is the more realistic choice for a document store: it
		// doesn't depend on the driver's id scheme, so it stays stable if this
		// document ever moves to another store or gets referenced from outside Mongo.
		Review review = Review.builder().id(UUID.randomUUID().toString())
				.eventId(eventId).rating(request.rating()).comment(request.comment()).build();
		Review saved = reviews.save(review);
		URI location = URI.create("/events/" + eventId + "/reviews/" + saved.getId());
		return ResponseEntity.created(location).body(toResponse(saved, event.name()));
	}

	@GetMapping
	public List<ReviewResponse> list(@PathVariable Long eventId) {
		EventResponse event = events.get(eventId);
		return reviews.findByEventId(eventId).stream().map(review -> toResponse(review, event.name())).toList();
	}

	@GetMapping("/summary")
	public ReviewSummary summary(@PathVariable Long eventId) {
		return reviews.summarizeByEventId(eventId).orElse(new ReviewSummary(0.0, 0));
	}

	private static ReviewResponse toResponse(Review review, String eventName) {
		return new ReviewResponse(review.getId(), review.getEventId(), review.getRating(), review.getComment(), eventName);
	}

}
