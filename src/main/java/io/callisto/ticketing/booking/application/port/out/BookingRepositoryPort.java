package io.callisto.ticketing.booking.application.port.out;

import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingId;

import java.util.Optional;

public interface BookingRepositoryPort {

	Booking save(Booking booking);

	Optional<Booking> findById(BookingId id);

}
