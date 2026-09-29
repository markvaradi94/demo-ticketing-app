package io.callisto.ticketing.booking.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(int maxSeatsPerBooking) {
}
