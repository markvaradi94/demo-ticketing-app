package io.callisto.ticketing.review;

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

@RestController
@RequestMapping("/events/{eventId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviews;

	@PostMapping
	public ResponseEntity<ReviewResponse> create(@PathVariable Long eventId, @Valid @RequestBody ReviewRequest request) {
		ReviewResponse created = reviews.create(eventId, request);
		URI location = URI.create("/events/" + eventId + "/reviews/" + created.id());
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public List<ReviewResponse> list(@PathVariable Long eventId) {
		return reviews.list(eventId);
	}

	@GetMapping("/summary")
	public ReviewSummary summary(@PathVariable Long eventId) {
		return reviews.summary(eventId);
	}

}
