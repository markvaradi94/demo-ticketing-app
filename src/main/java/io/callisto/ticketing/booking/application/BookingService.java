package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.booking.adapter.out.persistence.BookingRepository;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingStatus;
import io.callisto.ticketing.catalog.Event;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Public — adapter.in.web.BookingController is a different package now that the
// hexagonal split is real, not just a compiler-enforced convention within one flat
// package the way session 4 left it.
//
// Still depends on BookingRepository and EventRepository directly, not through ports
// — that's this branch's starting gap, not yet fixed. EventRepository in particular
// reads *and mutates* bookedSeats on catalog's Event, a deeper coupling than a lookup;
// wrapping it behind a port here still leaves the actual invariant enforcement to
// session 6's DDD work (the EventInventory aggregate owning it as its own concern).
@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepository bookings;
	private final EventRepository events;
	private final BookingProperties bookingProperties;

	public Booking create(Booking newBooking) {
		Long eventId = newBooking.getEventId();
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));

		if (newBooking.getSeatCount() > bookingProperties.maxSeatsPerBooking()) {
			throw new TooManySeatsRequestedException(newBooking.getSeatCount(), bookingProperties.maxSeatsPerBooking());
		}

		events.save(event.withBookedSeats(event.getBookedSeats() + newBooking.getSeatCount()));

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
