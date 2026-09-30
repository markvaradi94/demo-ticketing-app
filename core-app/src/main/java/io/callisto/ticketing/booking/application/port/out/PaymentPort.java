package io.callisto.ticketing.booking.application.port.out;

import java.math.BigDecimal;

// One method, shaped around the one thing BookingService actually needs — same
// narrow, purpose-built reasoning as EventAvailabilityPort. Throws
// PaymentDeclinedException for a real decline (a legitimate business outcome, not a
// fault — see PaymentAdapter's ignore-exceptions config) or an unchecked exception
// for anything else (timeout, connection failure, 5xx), which Resilience4j's
// @Retry/@CircuitBreaker on the adapter side are free to act on.
public interface PaymentPort {

	void charge(String reference, BigDecimal amount);

}
