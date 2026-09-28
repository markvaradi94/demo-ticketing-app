package io.callisto.ticketing.booking.dto;

public record BookingResponse(String id, Long eventId, String customerName, int seatCount, String status) {
}
