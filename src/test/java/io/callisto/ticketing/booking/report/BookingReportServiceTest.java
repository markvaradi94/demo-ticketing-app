package io.callisto.ticketing.booking.report;

import io.callisto.ticketing.booking.BookingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingReportServiceTest {

	private final BookingReportService service = new BookingReportService();

	@Test
	void countsOnlyConfirmedBookingsTowardsRevenue() {
		BookingLine confirmed = line("evt-1", BookingStatus.CONFIRMED, 2, "25.00");
		BookingLine pending = line("evt-1", BookingStatus.PENDING, 1, "25.00");
		BookingLine cancelled = line("evt-1", BookingStatus.CANCELLED, 1, "25.00");

		BookingReport report = service.generateReport(List.of(confirmed, pending, cancelled));

		assertThat(report.totalBookings()).isEqualTo(3);
		assertThat(report.totalSeatsSold()).isEqualTo(2);
		assertThat(report.totalRevenue()).isEqualByComparingTo("50.00");
		assertThat(report.cancelledCount()).isEqualTo(1);
	}

	@Test
	void aggregatesRevenuePerEvent() {
		BookingLine eventOne = line("evt-1", BookingStatus.CONFIRMED, 1, "40.00");
		BookingLine eventTwo = line("evt-2", BookingStatus.CONFIRMED, 2, "10.00");

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

	private static BookingLine line(String eventId, BookingStatus status, int seatCount, String pricePerSeat) {
		return new BookingLine(eventId, status, seatCount, new BigDecimal(pricePerSeat));
	}

}
