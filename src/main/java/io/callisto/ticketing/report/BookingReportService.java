package io.callisto.ticketing.report;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookingReportService {

	public BookingReportResult generateReport(List<LegacyBooking> bookings) {
		BookingReportResult result = new BookingReportResult();
		if (bookings == null) {
			return result;
		}

		int total = 0;
		int seatsSold = 0;
		double revenue = 0.0;
		int cancelled = 0;
		Map<String, Double> revenueByEvent = new HashMap<>();

		for (LegacyBooking booking : bookings) {
			if (booking == null || booking.getStatus() == null) {
				continue;
			}

			total++;

			if (booking.getStatus().equals("CANCELLED")) {
				cancelled++;
				continue;
			}

			if (!booking.getStatus().equals("CONFIRMED")) {
				continue;
			}

			double bookingTotal = booking.getSeatCount() * booking.getPricePerSeat();
			seatsSold = seatsSold + booking.getSeatCount();
			revenue = revenue + bookingTotal;

			String eventId = booking.getEventId();
			if (eventId != null) {
				Double existing = revenueByEvent.get(eventId);
				if (existing == null) {
					revenueByEvent.put(eventId, bookingTotal);
				} else {
					revenueByEvent.put(eventId, existing + bookingTotal);
				}
			}
		}

		result.setTotalBookings(total);
		result.setTotalSeatsSold(seatsSold);
		result.setTotalRevenue(revenue);
		result.setCancelledCount(cancelled);
		result.setRevenueByEvent(revenueByEvent);
		return result;
	}

}
