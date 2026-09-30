package io.callisto.ticketing.booking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyTest {

	@Test
	void pendingBookingsAreFullyRefundable() {
		Money refund = RefundPolicy.refundAmount(BookingStatus.PENDING, Money.of("100.00"));

		assertThat(refund.amount()).isEqualByComparingTo("100.00");
	}

	@Test
	void confirmedBookingsAreHalfRefundable() {
		Money refund = RefundPolicy.refundAmount(BookingStatus.CONFIRMED, Money.of("100.00"));

		assertThat(refund.amount()).isEqualByComparingTo("50.00");
	}

	@Test
	void cancelledBookingsAreNotRefunded() {
		Money refund = RefundPolicy.refundAmount(BookingStatus.CANCELLED, Money.of("100.00"));

		assertThat(refund.amount()).isEqualByComparingTo("0");
	}

}
