package io.callisto.ticketing.messaging;

// Single source of truth for the exchange name and routing keys, so core-app's
// publisher and notification-service's queue binding can't silently drift from
// each other by each hardcoding the same strings independently.
public final class BookingRoutingKeys {

	public static final String EXCHANGE = "booking.events";
	public static final String CREATED = "booking.created";
	public static final String CANCELLED = "booking.cancelled";

	private BookingRoutingKeys() {
	}

}
