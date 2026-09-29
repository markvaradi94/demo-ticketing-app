package io.callisto.ticketing.booking.adapter.out.persistence;

import io.callisto.ticketing.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
