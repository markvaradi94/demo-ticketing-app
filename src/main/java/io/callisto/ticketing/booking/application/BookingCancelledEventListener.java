package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.domain.BookingCancelled;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Plain @EventListener, not @TransactionalEventListener(phase = AFTER_COMMIT) — the
// textbook-correct choice for a domain event, but cancel() isn't wrapped in an
// explicit @Transactional today (each port call commits on its own), and
// @TransactionalEventListener silently never fires with no active transaction unless
// paired with one. Firing synchronously right after cancel()'s own save matches how
// (un)transactional the existing code already is — not a regression, just consistent.
@Component
@RequiredArgsConstructor
class BookingCancelledEventListener {

	private final EventAvailabilityPort events;

	@EventListener
	void onBookingCancelled(BookingCancelled event) {
		events.releaseSeats(event.eventId(), event.seatCount());
	}

}
