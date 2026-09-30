package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.booking.application.port.out.BookingEventPublisherPort;
import io.callisto.ticketing.booking.application.port.out.BookingRepositoryPort;
import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.application.port.out.PaymentPort;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingCancelled;
import io.callisto.ticketing.booking.domain.BookingId;
import io.callisto.ticketing.booking.domain.BookingStatus;
import io.callisto.ticketing.booking.domain.SeatCount;
import io.callisto.ticketing.catalog.EventClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

// Public — adapter.in.web.BookingController is a different package now that the
// hexagonal split is real, not just a compiler-enforced convention within one flat
// package the way session 4 left it.
//
// Depends on the three outbound ports, not on any adapter.* type directly —
// BookingArchitectureTests enforces that with an ArchUnit layeredArchitecture()
// rule. EventClient is different: it's catalog's own narrow, purpose-built public
// client (session 4), used directly the same way ReviewService already does for
// nameOf() — not every cross-module read needs a booking-owned port, only the ones
// wrapping an actual adapter-layer external system.
@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepositoryPort bookings;
	private final EventAvailabilityPort events;
	private final PaymentPort payments;
	private final BookingEventPublisherPort bookingEvents;
	private final EventClient eventClient;
	private final BookingProperties bookingProperties;
	private final ApplicationEventPublisher eventPublisher;

	public Booking create(Booking newBooking) {
		if (newBooking.getSeatCount() > bookingProperties.maxSeatsPerBooking()) {
			throw new TooManySeatsRequestedException(newBooking.getSeatCount(), bookingProperties.maxSeatsPerBooking());
		}

		SeatCount seatCount = SeatCount.of(newBooking.getSeatCount());
		events.reserveSeats(newBooking.getEventId(), seatCount);

		BigDecimal amount = eventClient.pricePerSeat(newBooking.getEventId()).multiply(BigDecimal.valueOf(newBooking.getSeatCount()));
		try {
			payments.charge(UUID.randomUUID().toString(), amount);
		} catch (RuntimeException e) {
			// Seats were already reserved above — undo that before letting the
			// failure propagate, same releaseSeats() session 6 built for cancel().
			events.releaseSeats(newBooking.getEventId(), seatCount);
			throw e;
		}

		Booking saved = bookings.save(newBooking);
		bookingEvents.publishCreated(saved);
		return saved;
	}

	public Booking get(Long eventId, BookingId bookingId) {
		return findOrThrow(eventId, bookingId);
	}

	public Booking cancel(Long eventId, BookingId bookingId) {
		Booking booking = findOrThrow(eventId, bookingId);

		if (booking.getStatus() == BookingStatus.CANCELLED) {
			throw new BookingAlreadyCancelledException(bookingId.value());
		}

		Booking cancelled = bookings.save(booking.toBuilder().status(BookingStatus.CANCELLED).build());
		// Two independent reactions to the same moment, through two different
		// mechanisms: the in-JVM ApplicationEvent (session 6) releases seats within
		// core-app itself; publishCancelled (this session) tells the outside world.
		eventPublisher.publishEvent(new BookingCancelled(cancelled.getEventId(), SeatCount.of(cancelled.getSeatCount())));
		bookingEvents.publishCancelled(cancelled);
		return cancelled;
	}

	private Booking findOrThrow(Long eventId, BookingId bookingId) {
		Booking booking = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId.value()));
		if (!booking.getEventId().equals(eventId)) {
			throw new BookingNotFoundException(bookingId.value());
		}
		return booking;
	}

}
