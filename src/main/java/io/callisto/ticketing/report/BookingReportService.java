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

		for (int i = 0; i < bookings.size(); i++) {
			LegacyBooking booking = bookings.get(i);
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

			int seatsInBooking = 0;
			if (booking.getSeatLabels() != null) {
				for (int j = 0; j < booking.getSeatLabels().size(); j++) {
					if (booking.getSeatLabels().get(j) != null) {
						seatsInBooking++;
					}
				}
			}

			double lineTotal = seatsInBooking * booking.getPricePerSeat();
			seatsSold = seatsSold + seatsInBooking;
			revenue = revenue + lineTotal;

			String eventId = booking.getEventId();
			if (eventId != null) {
				Double existing = revenueByEvent.get(eventId);
				if (existing == null) {
					revenueByEvent.put(eventId, lineTotal);
				} else {
					revenueByEvent.put(eventId, existing + lineTotal);
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
