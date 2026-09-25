package io.callisto.ticketing.report;

import io.callisto.ticketing.domain.BookingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingReportServiceTest {

	private final BookingReportService service = new BookingReportService();

	@Test
	void countsOnlyConfirmedBookingsTowardsRevenue() {
		BookingLine confirmed = line("evt-1", new BookingStatus.Confirmed(), List.of("A1", "A2"), "25.00");
		BookingLine pending = line("evt-1", new BookingStatus.Pending(), List.of("B1"), "25.00");
		BookingLine cancelled = line("evt-1", new BookingStatus.Cancelled(), List.of("C1"), "25.00");

		BookingReport report = service.generateReport(List.of(confirmed, pending, cancelled));

		assertThat(report.totalBookings()).isEqualTo(3);
		assertThat(report.totalSeatsSold()).isEqualTo(2);
		assertThat(report.totalRevenue()).isEqualByComparingTo("50.00");
		assertThat(report.cancelledCount()).isEqualTo(1);
	}

	@Test
	void aggregatesRevenuePerEvent() {
		BookingLine eventOne = line("evt-1", new BookingStatus.Confirmed(), List.of("A1"), "40.00");
		BookingLine eventTwo = line("evt-2", new BookingStatus.Confirmed(), List.of("A1", "A2"), "10.00");

		BookingReport report = service.generateReport(List.of(eventOne, eventTwo));

		assertThat(report.revenueByEvent())
				.containsEntry("evt-1", new BigDecimal("40.00"))
				.containsEntry("evt-2", new BigDecimal("20.00"));
	}

	@Test
	void returnsEmptyReportForNoBookings() {
		BookingReport report = service.generateReport(List.of());

		assertThat(report.totalBookings()).isZero();
		assertThat(report.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(report.revenueByEvent()).isEmpty();
	}

	private static BookingLine line(String eventId, BookingStatus status, List<String> seatLabels, String pricePerSeat) {
		return new BookingLine(eventId, status, seatLabels, new BigDecimal(pricePerSeat));
	}

}
