package io.callisto.ticketing.report;

import io.callisto.ticketing.domain.BookingStatus;

import java.math.BigDecimal;
import java.util.List;

public record BookingLine(String eventId, BookingStatus status, List<String> seatLabels, BigDecimal pricePerSeat) {

	public BigDecimal lineTotal() {
		return pricePerSeat.multiply(BigDecimal.valueOf(seatLabels.size()));
	}

}
