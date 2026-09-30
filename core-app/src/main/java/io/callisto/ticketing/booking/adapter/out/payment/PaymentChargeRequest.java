package io.callisto.ticketing.booking.adapter.out.payment;

import java.math.BigDecimal;

// Mirrors payment-service's own PaymentRequest by convention, not by a shared Java
// type — unlike BookingCreatedMessage/BookingCancelledMessage in `shared`, an HTTP
// request/response body isn't something both sides need to compile against directly,
// only agree on the JSON shape of. That's a deliberate, different sharing strategy
// from the RabbitMQ contracts, not an oversight.
record PaymentChargeRequest(String reference, BigDecimal amount) {
}
