package io.callisto.paymentservice;

import java.math.BigDecimal;

// A fake gateway's decision, driven entirely by the charged amount — no admin flag,
// no test-only header. Deterministic and demoable through the domain itself: an
// event's pricePerSeat times a booking's seatCount decides which of the three
// behaviors core-app's Resilience4j wiring has to handle.
enum PaymentOutcome {

	APPROVE,
	APPROVE_SLOWLY,
	DECLINE;

	private static final BigDecimal SLOW_THRESHOLD = new BigDecimal("1000");
	private static final BigDecimal DECLINE_THRESHOLD = new BigDecimal("2000");

	static PaymentOutcome forAmount(BigDecimal amount) {
		if (amount.compareTo(DECLINE_THRESHOLD) >= 0) {
			return DECLINE;
		}
		if (amount.compareTo(SLOW_THRESHOLD) >= 0) {
			return APPROVE_SLOWLY;
		}
		return APPROVE;
	}

}
