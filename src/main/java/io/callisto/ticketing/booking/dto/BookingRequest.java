package io.callisto.ticketing.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record BookingRequest(@NotBlank String customerName, @Positive int seatCount) {
}
