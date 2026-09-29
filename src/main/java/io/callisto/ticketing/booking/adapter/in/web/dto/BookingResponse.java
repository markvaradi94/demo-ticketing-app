package io.callisto.ticketing.booking.adapter.in.web.dto;

public record BookingResponse(Long id, Long eventId, String customerName, int seatCount, String status) {
}
