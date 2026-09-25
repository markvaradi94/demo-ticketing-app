package io.callisto.ticketing.domain;

public sealed interface BookingStatus permits BookingStatus.Pending, BookingStatus.Confirmed, BookingStatus.Cancelled {

	record Pending() implements BookingStatus {
	}

	record Confirmed() implements BookingStatus {
	}

	record Cancelled() implements BookingStatus {
	}

}
