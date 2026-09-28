package io.callisto.ticketing.report;

import io.callisto.ticketing.domain.BookingStatus;

import java.math.BigDecimal;

public record BookingLine(String eventId, BookingStatus status, int seatCount, BigDecimal pricePerSeat) {

	public BigDecimal bookingTotal() {
		return pricePerSeat.multiply(BigDecimal.valueOf(seatCount));
	}

}
