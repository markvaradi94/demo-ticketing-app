package io.callisto.ticketing.booking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyTest {

	@Test
	void pendingBookingsAreFullyRefundable() {
		BigDecimal refund = RefundPolicy.refundAmount(BookingStatus.PENDING, new BigDecimal("100.00"));

		assertThat(refund).isEqualByComparingTo("100.00");
	}

	@Test
	void confirmedBookingsAreHalfRefundable() {
		BigDecimal refund = RefundPolicy.refundAmount(BookingStatus.CONFIRMED, new BigDecimal("100.00"));

		assertThat(refund).isEqualByComparingTo("50.00");
	}

	@Test
	void cancelledBookingsAreNotRefunded() {
		BigDecimal refund = RefundPolicy.refundAmount(BookingStatus.CANCELLED, new BigDecimal("100.00"));

		assertThat(refund).isEqualByComparingTo("0");
	}

}
