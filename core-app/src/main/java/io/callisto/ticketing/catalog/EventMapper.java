package io.callisto.ticketing.catalog;

import io.callisto.ticketing.catalog.dto.EventRequest;
import io.callisto.ticketing.catalog.dto.EventResponse;

// Package-private on purpose — a mapper is an internal collaborator for this
// module's own controller and service, never something another module should call.
// Being package-private also means this is enforced by the compiler, not just
// Spring Modulith's convention: no other module's code could even reference it.
final class EventMapper {

	private EventMapper() {
	}

	static Event toNewEvent(EventRequest request) {
		Venue venue = Venue.builder().name(request.venueName()).capacity(request.venueCapacity()).build();
		return Event.builder().name(request.name()).venue(venue).pricePerSeat(request.pricePerSeat()).startTime(request.startTime()).build();
	}

	// toBuilder() off the loaded entity, not a fresh builder() — carries over id
	// *and* version. Rebuilding from scratch left version null, which Spring Data
	// reads as "this is a new entity" once Event has an @Version field, and
	// persist() on an id that already exists blows up instead of updating it.
	static Event applyUpdate(Event existing, EventRequest request) {
		Venue venue = Venue.builder().name(request.venueName()).capacity(request.venueCapacity()).build();
		return existing.toBuilder().name(request.name()).venue(venue).pricePerSeat(request.pricePerSeat()).startTime(request.startTime()).build();
	}

	static EventResponse toResponse(Event event) {
		return new EventResponse(event.getId(), event.getName(), event.getVenue().getName(), event.getVenue().getCapacity(), event.getPricePerSeat(), event.getStartTime());
	}

}
