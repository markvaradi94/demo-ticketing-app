package io.callisto.paymentservice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOutcomeTest {

	@Test
	void approvesImmediatelyBelowTheSlowThreshold() {
		assertThat(PaymentOutcome.forAmount(new BigDecimal("999.99"))).isEqualTo(PaymentOutcome.APPROVE);
	}

	@Test
	void approvesSlowlyAtOrAboveTheSlowThreshold() {
		assertThat(PaymentOutcome.forAmount(new BigDecimal("1000.00"))).isEqualTo(PaymentOutcome.APPROVE_SLOWLY);
		assertThat(PaymentOutcome.forAmount(new BigDecimal("1999.99"))).isEqualTo(PaymentOutcome.APPROVE_SLOWLY);
	}

	@Test
	void declinesAtOrAboveTheDeclineThreshold() {
		assertThat(PaymentOutcome.forAmount(new BigDecimal("2000.00"))).isEqualTo(PaymentOutcome.DECLINE);
	}

}
