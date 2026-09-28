package io.callisto.ticketing.catalog;

import io.callisto.ticketing.catalog.dto.EventRequest;
import io.callisto.ticketing.catalog.dto.EventResponse;
import io.callisto.ticketing.domain.Event;
import io.callisto.ticketing.domain.Venue;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

	private final EventRepository events;

	@PostMapping
	public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
		Event event = events.save(toNewEvent(request));
		return ResponseEntity.created(URI.create("/events/" + event.getId())).body(toResponse(event));
	}

	@GetMapping
	public List<EventResponse> list() {
		return events.findAll().stream().map(EventController::toResponse).toList();
	}

	@GetMapping("/{id}")
	public EventResponse get(@PathVariable Long id) {
		return toResponse(findOrThrow(id));
	}

	@PutMapping("/{id}")
	public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
		Event existing = findOrThrow(id);
		Venue venue = Venue.builder().name(request.venueName()).capacity(request.venueCapacity()).build();
		// toBuilder() off the loaded entity, not a fresh builder() — carries over id
		// *and* version. Rebuilding from scratch left version null, which Spring Data
		// reads as "this is a new entity" once Event has an @Version field, and
		// persist() on an id that already exists blows up instead of updating it.
		Event updated = existing.toBuilder().name(request.name()).venue(venue).startTime(request.startTime()).build();
		return toResponse(events.save(updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		findOrThrow(id);
		events.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	private Event findOrThrow(Long id) {
		return events.findById(id).orElseThrow(() -> new EventNotFoundException(id));
	}

	private static Event toNewEvent(EventRequest request) {
		Venue venue = Venue.builder().name(request.venueName()).capacity(request.venueCapacity()).build();
		return Event.builder().name(request.name()).venue(venue).startTime(request.startTime()).build();
	}

	private static EventResponse toResponse(Event event) {
		return new EventResponse(event.getId(), event.getName(), event.getVenue().getName(), event.getVenue().getCapacity(), event.getStartTime());
	}

}
