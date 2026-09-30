package io.callisto.ticketing.booking.domain;

public record BookingCancelled(Long eventId, SeatCount seatCount) {
}
