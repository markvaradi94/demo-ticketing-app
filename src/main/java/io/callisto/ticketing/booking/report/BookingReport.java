package io.callisto.ticketing.booking.report;

import java.math.BigDecimal;
import java.util.Map;

public record BookingReport(
		int totalBookings,
		int totalSeatsSold,
		BigDecimal totalRevenue,
		int cancelledCount,
		Map<String, BigDecimal> revenueByEvent) {

}
