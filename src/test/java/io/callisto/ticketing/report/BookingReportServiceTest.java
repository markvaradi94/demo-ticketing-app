package io.callisto.ticketing.report;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingReportServiceTest {

	private final BookingReportService service = new BookingReportService();

	@Test
	void countsOnlyConfirmedBookingsTowardsRevenue() {
		LegacyBooking confirmed = booking("evt-1", "CONFIRMED", List.of("A1", "A2"), 25.0);
		LegacyBooking pending = booking("evt-1", "PENDING", List.of("B1"), 25.0);
		LegacyBooking cancelled = booking("evt-1", "CANCELLED", List.of("C1"), 25.0);

		BookingReportResult result = service.generateReport(List.of(confirmed, pending, cancelled));

		assertThat(result.getTotalBookings()).isEqualTo(3);
		assertThat(result.getTotalSeatsSold()).isEqualTo(2);
		assertThat(result.getTotalRevenue()).isEqualTo(50.0);
		assertThat(result.getCancelledCount()).isEqualTo(1);
	}

	@Test
	void aggregatesRevenuePerEvent() {
		LegacyBooking eventOne = booking("evt-1", "CONFIRMED", List.of("A1"), 40.0);
		LegacyBooking eventTwo = booking("evt-2", "CONFIRMED", List.of("A1", "A2"), 10.0);

		BookingReportResult result = service.generateReport(List.of(eventOne, eventTwo));

		assertThat(result.getRevenueByEvent())
				.containsEntry("evt-1", 40.0)
				.containsEntry("evt-2", 20.0);
	}

	@Test
	void returnsEmptyReportForNoBookings() {
		BookingReportResult result = service.generateReport(List.of());

		assertThat(result.getTotalBookings()).isZero();
		assertThat(result.getTotalRevenue()).isZero();
		assertThat(result.getRevenueByEvent()).isEmpty();
	}

	@Test
	void toleratesNullEntriesAndMissingStatus() {
		LegacyBooking missingStatus = new LegacyBooking();
		missingStatus.setEventId("evt-1");

		BookingReportResult result = service.generateReport(Arrays.asList(missingStatus, null));

		assertThat(result.getTotalBookings()).isZero();
	}

	private static LegacyBooking booking(String eventId, String status, List<String> seatLabels, double pricePerSeat) {
		LegacyBooking booking = new LegacyBooking();
		booking.setEventId(eventId);
		booking.setStatus(status);
		booking.setSeatLabels(seatLabels);
		booking.setPricePerSeat(pricePerSeat);
		return booking;
	}

}
