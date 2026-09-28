package io.callisto.ticketing.review;

import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
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
	private final EventRepository events;

	@PostMapping
	public ResponseEntity<ReviewResponse> create(@PathVariable Long eventId, @Valid @RequestBody ReviewRequest request) {
		if (!events.existsById(eventId)) {
			throw new EventNotFoundException(eventId);
		}

		// We assign the id ourselves here, unlike Event/Booking — MongoDB would
		// generate its own ObjectId-backed string automatically if left null, but a
		// self-assigned UUID is the more realistic choice for a document store: it
		// doesn't depend on the driver's id scheme, so it stays stable if this
		// document ever moves to another store or gets referenced from outside Mongo.
		Review review = Review.builder().id(UUID.randomUUID().toString())
				.eventId(eventId).rating(request.rating()).comment(request.comment()).build();
		Review saved = reviews.save(review);
		URI location = URI.create("/events/" + eventId + "/reviews/" + saved.getId());
		return ResponseEntity.created(location).body(toResponse(saved));
	}

	@GetMapping
	public List<ReviewResponse> list(@PathVariable Long eventId) {
		return reviews.findByEventId(eventId).stream().map(ReviewController::toResponse).toList();
	}

	@GetMapping("/summary")
	public ReviewSummary summary(@PathVariable Long eventId) {
		return reviews.summarizeByEventId(eventId).orElse(new ReviewSummary(0.0, 0));
	}

	private static ReviewResponse toResponse(Review review) {
		return new ReviewResponse(review.getId(), review.getEventId(), review.getRating(), review.getComment());
	}

}
