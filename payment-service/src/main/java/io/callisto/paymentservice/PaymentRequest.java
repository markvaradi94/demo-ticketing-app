package io.callisto.paymentservice;

import java.math.BigDecimal;

// reference, not bookingId — this gateway has no notion of what a "booking" is, only
// a caller-supplied reference to echo back in logs/responses. Keeping the domain
// vocabulary out of the contract is deliberate: core-app generates a fresh reference
// per charge attempt rather than leaking its own booking id across the service
// boundary.
public record PaymentRequest(String reference, BigDecimal amount) {
}
