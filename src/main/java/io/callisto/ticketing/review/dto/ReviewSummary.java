package io.callisto.ticketing.review.dto;

// Doubles as the shape returned by ReviewRepository's aggregation projection and
// the API response — a pure value with no identity, so one record is enough.
public record ReviewSummary(double averageRating, long totalReviews) {
}
