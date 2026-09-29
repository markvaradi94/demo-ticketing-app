package io.callisto.ticketing.booking;

import io.callisto.ticketing.booking.dto.BookingRequest;
import io.callisto.ticketing.booking.dto.BookingResponse;

// Package-private — internal to this module's own controller and service.
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
