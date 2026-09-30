package io.callisto.ticketing.messaging;

// The contract core-app publishes and notification-service consumes — a plain
// record with no Spring/AMQP dependency, so it's the one thing both sides of the
// RabbitMQ boundary compile against directly instead of agreeing on a JSON shape by
// convention alone. This is the reason `shared` is a real Gradle module now, not a
// Modulith package: a package inside core-app's jar is invisible to a different jar.
public record BookingCreatedMessage(Long bookingId, Long eventId, String customerName, int seatCount) {
}
