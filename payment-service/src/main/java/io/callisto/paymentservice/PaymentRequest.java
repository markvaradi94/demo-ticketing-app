package io.callisto.paymentservice;

import java.math.BigDecimal;

public record PaymentRequest(Long bookingId, BigDecimal amount) {
}
