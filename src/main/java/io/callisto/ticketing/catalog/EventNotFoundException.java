package io.callisto.ticketing.catalog;

public class EventNotFoundException extends RuntimeException {

	public EventNotFoundException(String eventId) {
		super("No event with id " + eventId);
	}

}
