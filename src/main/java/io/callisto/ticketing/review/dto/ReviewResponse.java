package io.callisto.ticketing.review.dto;

public record ReviewResponse(String id, Long eventId, int rating, String comment, String eventName) {
}
