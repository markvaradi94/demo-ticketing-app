package io.callisto.ticketing.catalog;

import io.callisto.ticketing.catalog.dto.EventRequest;
import io.callisto.ticketing.catalog.dto.EventResponse;
import io.callisto.ticketing.domain.Event;
import io.callisto.ticketing.domain.Venue;
import jakarta.validation.Valid;
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
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {

	private final EventRepository events;

	public EventController(EventRepository events) {
		this.events = events;
	}

	@PostMapping
	public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
		Event event = events.save(toNewEvent(request));
		return ResponseEntity.created(URI.create("/events/" + event.id())).body(toResponse(event));
	}

	@GetMapping
	public List<EventResponse> list() {
		return events.findAll().stream().map(EventController::toResponse).toList();
	}

	@GetMapping("/{id}")
	public EventResponse get(@PathVariable String id) {
		return toResponse(findOrThrow(id));
	}

	@PutMapping("/{id}")
	public EventResponse update(@PathVariable String id, @Valid @RequestBody EventRequest request) {
		findOrThrow(id);
		Venue venue = new Venue(UUID.randomUUID().toString(), request.venueName(), request.venueCapacity());
		Event updated = new Event(id, request.name(), venue, request.startTime());
		return toResponse(events.save(updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		findOrThrow(id);
		events.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	private Event findOrThrow(String id) {
		return events.findById(id).orElseThrow(() -> new EventNotFoundException(id));
	}

	private static Event toNewEvent(EventRequest request) {
		Venue venue = new Venue(UUID.randomUUID().toString(), request.venueName(), request.venueCapacity());
		return new Event(null, request.name(), venue, request.startTime());
	}

	private static EventResponse toResponse(Event event) {
		return new EventResponse(event.id(), event.name(), event.venue().name(), event.venue().capacity(), event.startTime());
	}

}
