package io.callisto.ticketing.catalog;

import io.callisto.ticketing.domain.Event;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class EventRepository {

	private final Map<String, Event> events = new ConcurrentHashMap<>();

	public Event save(Event event) {
		Event stored = event.id() != null ? event : withGeneratedId(event);
		events.put(stored.id(), stored);
		return stored;
	}

	public Optional<Event> findById(String id) {
		return Optional.ofNullable(events.get(id));
	}

	public List<Event> findAll() {
		return List.copyOf(events.values());
	}

	public void deleteById(String id) {
		events.remove(id);
	}

	private Event withGeneratedId(Event event) {
		return new Event(UUID.randomUUID().toString(), event.name(), event.venue(), event.startTime());
	}

}
