package io.callisto.ticketing.report;

import io.callisto.ticketing.domain.BookingStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class BookingReportService {

	public BookingReport generateReport(List<BookingLine> bookings) {
		int totalBookings = bookings.size();

		int cancelledCount = (int) bookings.stream()
				.filter(line -> line.status() == BookingStatus.CANCELLED)
				.count();

		List<BookingLine> confirmed = bookings.stream()
				.filter(line -> line.status() == BookingStatus.CONFIRMED)
				.toList();

		int totalSeatsSold = confirmed.stream()
				.mapToInt(BookingLine::seatCount)
				.sum();

		BigDecimal totalRevenue = confirmed.stream()
				.map(BookingLine::bookingTotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		Map<String, BigDecimal> revenueByEvent = confirmed.stream()
				.collect(Collectors.groupingBy(
						BookingLine::eventId,
						TreeMap::new,
						Collectors.reducing(BigDecimal.ZERO, BookingLine::bookingTotal, BigDecimal::add)));

		return new BookingReport(totalBookings, totalSeatsSold, totalRevenue, cancelledCount, revenueByEvent);
	}

}
