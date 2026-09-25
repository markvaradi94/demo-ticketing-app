package io.callisto.ticketing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyTest {

	@Test
	void pendingBookingsAreFullyRefundable() {
		BigDecimal refund = RefundPolicy.refundAmount(new BookingStatus.Pending(), new BigDecimal("100.00"));

		assertThat(refund).isEqualByComparingTo("100.00");
	}

	@Test
	void confirmedBookingsAreHalfRefundable() {
		BigDecimal refund = RefundPolicy.refundAmount(new BookingStatus.Confirmed(), new BigDecimal("100.00"));

		assertThat(refund).isEqualByComparingTo("50.00");
	}

	@Test
	void cancelledBookingsAreNotRefunded() {
		BigDecimal refund = RefundPolicy.refundAmount(new BookingStatus.Cancelled(), new BigDecimal("100.00"));

		assertThat(refund).isEqualByComparingTo("0");
	}

}
