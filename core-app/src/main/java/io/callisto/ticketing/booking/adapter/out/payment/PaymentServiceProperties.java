package io.callisto.ticketing.booking.adapter.out.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Infrastructure config for this one adapter's outbound HTTP client — deliberately
// not alongside BookingProperties in application/, which holds an actual business
// rule (max seats per booking), not connection details.
@ConfigurationProperties(prefix = "payment-service")
record PaymentServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
