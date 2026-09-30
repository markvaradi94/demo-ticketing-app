package io.callisto.ticketing.booking.adapter.in.web;

import io.callisto.ticketing.booking.adapter.in.web.dto.BookingRequest;
import io.callisto.ticketing.booking.adapter.in.web.dto.BookingResponse;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingStatus;

// Package-private — internal to this adapter's own controller. Owns both directions
// of DTO <-> domain mapping so BookingRequest/BookingResponse never cross into
// application — BookingService only ever sees a Booking.
final class BookingMapper {

	private BookingMapper() {
	}

	static Booking toNewBooking(Long eventId, BookingRequest request) {
		// No id set — generated via the bookings table's identity column, same
		// convention as Event/Venue.
		return Booking.builder()
				.eventId(eventId)
				.customerName(request.customerName())
				.seatCount(request.seatCount())
				.status(BookingStatus.CONFIRMED)
				.build();
	}

	static BookingResponse toResponse(Booking booking) {
		return new BookingResponse(booking.getId(), booking.getEventId(), booking.getCustomerName(), booking.getSeatCount(), booking.getStatus().name());
	}

}
