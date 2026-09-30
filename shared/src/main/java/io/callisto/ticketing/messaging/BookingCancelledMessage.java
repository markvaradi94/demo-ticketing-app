package io.callisto.ticketing.messaging;

public record BookingCancelledMessage(Long bookingId, Long eventId, String customerName) {
}
