package io.callisto.ticketing.booking;

import io.callisto.ticketing.booking.dto.BookingRequest;
import io.callisto.ticketing.catalog.Event;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Package-private — BookingController is the only caller within this module.
//
// Still depends on EventRepository directly, not a narrow client — this one reads
// *and mutates* bookedSeats on catalog's Event, which is a deeper coupling than a
// lookup. That belongs to session 6's DDD work (the EventInventory aggregate owning
// the no-overbooking invariant as its own concern), not something to paper over here
// with a wider client API.
@Service
@RequiredArgsConstructor
class BookingService {

	private final BookingRepository bookings;
	private final EventRepository events;
	private final BookingProperties bookingProperties;

	Booking create(Long eventId, BookingRequest request) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));

		if (request.seatCount() > bookingProperties.maxSeatsPerBooking()) {
			throw new TooManySeatsRequestedException(request.seatCount(), bookingProperties.maxSeatsPerBooking());
		}

		events.save(event.withBookedSeats(event.getBookedSeats() + request.seatCount()));

		return bookings.save(BookingMapper.toNewBooking(eventId, request));
	}

	Booking get(Long eventId, Long bookingId) {
		return findOrThrow(eventId, bookingId);
	}

	Booking cancel(Long eventId, Long bookingId) {
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
