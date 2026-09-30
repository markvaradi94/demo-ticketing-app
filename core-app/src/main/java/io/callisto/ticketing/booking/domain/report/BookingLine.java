package io.callisto.ticketing.booking.domain.report;

import io.callisto.ticketing.booking.domain.BookingStatus;

import java.math.BigDecimal;

public record BookingLine(String eventId, BookingStatus status, int seatCount, BigDecimal pricePerSeat) {

	public BigDecimal bookingTotal() {
		return pricePerSeat.multiply(BigDecimal.valueOf(seatCount));
	}

}
