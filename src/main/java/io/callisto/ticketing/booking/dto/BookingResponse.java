package io.callisto.ticketing.booking.dto;

public record BookingResponse(String id, String eventId, String customerName, int seatCount, String status) {
}
