package io.callisto.notificationservice;

import io.callisto.ticketing.messaging.BookingCancelledMessage;
import io.callisto.ticketing.messaging.BookingCreatedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Deliberately in-memory — a real system would persist processed ids (or lean on
// the notification side effect itself being naturally idempotent), since a restart
// forgets everything and a second instance in a scaled-out deployment wouldn't
// share this set at all. Documented as a scoped simplification for this course, not
// an oversight, same spirit as every other "here's the honest boundary" note
// elsewhere in this codebase.
@Component
class BookingEventsListener {

	private static final Logger log = LoggerFactory.getLogger(BookingEventsListener.class);

	private final Set<Long> processedBookingIds = ConcurrentHashMap.newKeySet();

	@RabbitListener(queues = "notification.booking-created")
	void onBookingCreated(BookingCreatedMessage message) {
		if (alreadyProcessed(message.bookingId())) {
			log.info("Skipping duplicate notification for booking {}", message.bookingId());
			return;
		}
		log.info("Notification: booking {} confirmed for {} — {} seat(s) on event {}",
				message.bookingId(), message.customerName(), message.seatCount(), message.eventId());
	}

	@RabbitListener(queues = "notification.booking-cancelled")
	void onBookingCancelled(BookingCancelledMessage message) {
		log.info("Notification: booking {} cancelled for {} on event {}",
				message.bookingId(), message.customerName(), message.eventId());
	}

	// Package-private so the dedup logic itself — the actual thing worth testing —
	// is directly callable without needing to intercept log output.
	boolean alreadyProcessed(Long bookingId) {
		return !processedBookingIds.add(bookingId);
	}

	// Read-only, unlike alreadyProcessed() above — exists purely so an integration
	// test can observe whether the real @RabbitListener got to a message without
	// the observation itself mutating the set it's checking.
	boolean hasBeenProcessed(Long bookingId) {
		return processedBookingIds.contains(bookingId);
	}

}
