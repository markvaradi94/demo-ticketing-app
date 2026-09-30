package io.callisto.ticketing.booking.adapter.out.persistence;

import io.callisto.ticketing.booking.application.port.out.BookingRepositoryPort;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Package-private — only BookingRepositoryPort needs to be public for BookingService
// (a different package) to depend on it. Spring instantiates and injects this by its
// interface type via reflection, which doesn't care about the class's own visibility.
@Component
@RequiredArgsConstructor
class BookingRepositoryAdapter implements BookingRepositoryPort {

	private final BookingRepository bookings;

	@Override
	public Booking save(Booking booking) {
		return bookings.save(booking);
	}

	@Override
	public Optional<Booking> findById(BookingId id) {
		return bookings.findById(id.value());
	}

}
