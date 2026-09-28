package io.callisto.ticketing.booking.dto;

public record BookingResponse(Long id, Long eventId, String customerName, int seatCount, String status) {
}
