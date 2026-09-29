package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.booking.application.port.out.BookingRepositoryPort;
import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Public — adapter.in.web.BookingController is a different package now that the
// hexagonal split is real, not just a compiler-enforced convention within one flat
// package the way session 4 left it.
//
// Depends only on the two outbound ports now, not on any adapter.* type or on
// catalog directly — BookingArchitectureTests enforces that with an ArchUnit
// layeredArchitecture() rule. EventAvailabilityPort's adapter still reaches
// catalog.EventRepository directly to read *and mutate* bookedSeats, a deeper
// coupling than a lookup; session 6's EventInventory aggregate replaces that one
// adapter class without this class or the port changing at all.
@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepositoryPort bookings;
	private final EventAvailabilityPort events;
	private final BookingProperties bookingProperties;

	public Booking create(Booking newBooking) {
		if (newBooking.getSeatCount() > bookingProperties.maxSeatsPerBooking()) {
			throw new TooManySeatsRequestedException(newBooking.getSeatCount(), bookingProperties.maxSeatsPerBooking());
		}

		events.reserveSeats(newBooking.getEventId(), newBooking.getSeatCount());

		return bookings.save(newBooking);
	}

	public Booking get(Long eventId, Long bookingId) {
		return findOrThrow(eventId, bookingId);
	}

	public Booking cancel(Long eventId, Long bookingId) {
		Booking booking = findOrThrow(eventId, bookingId);
		if (booking.getStatus() == BookingStatus.CANCELLED) {
			throw new BookingAlreadyCancelledException(bookingId);
		}
		return bookings.save(booking.toBuilder().status(BookingStatus.CANCELLED).build());
	}

	private Booking findOrThrow(Long eventId, Long bookingId) {
		Booking booking = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
		if (!booking.getEventId().equals(eventId)) {
			throw new BookingNotFoundException(bookingId);
		}
		return booking;
	}

}
