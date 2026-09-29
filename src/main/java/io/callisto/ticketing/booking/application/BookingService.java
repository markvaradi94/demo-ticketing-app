package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.booking.application.port.out.BookingRepositoryPort;
import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingId;
import io.callisto.ticketing.booking.domain.BookingStatus;
import io.callisto.ticketing.booking.domain.SeatCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Public — adapter.in.web.BookingController is a different package now that the
// hexagonal split is real, not just a compiler-enforced convention within one flat
// package the way session 4 left it.
//
// Depends only on the two outbound ports now, not on any adapter.* type or on
// catalog directly — BookingArchitectureTests enforces that with an ArchUnit
// layeredArchitecture() rule. EventAvailabilityPort's adapter still reaches
// catalog.EventRepository directly to read *and mutate* bookedSeats, and still
// doesn't check capacity at all — this session's actual gap, fixed by replacing
// that one adapter's insides, not this class or the port.
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

		events.reserveSeats(newBooking.getEventId(), SeatCount.of(newBooking.getSeatCount()));

		return bookings.save(newBooking);
	}

	public Booking get(Long eventId, BookingId bookingId) {
		return findOrThrow(eventId, bookingId);
	}

	public Booking cancel(Long eventId, BookingId bookingId) {
		Booking booking = findOrThrow(eventId, bookingId);
		if (booking.getStatus() == BookingStatus.CANCELLED) {
			throw new BookingAlreadyCancelledException(bookingId.value());
		}
		return bookings.save(booking.toBuilder().status(BookingStatus.CANCELLED).build());
	}

	private Booking findOrThrow(Long eventId, BookingId bookingId) {
		Booking booking = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId.value()));
		if (!booking.getEventId().equals(eventId)) {
			throw new BookingNotFoundException(bookingId.value());
		}
		return booking;
	}

}
