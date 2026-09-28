package io.callisto.ticketing.catalog;

public class EventNotFoundException extends RuntimeException {

	public EventNotFoundException(Long eventId) {
		super("No event with id " + eventId);
	}

}
