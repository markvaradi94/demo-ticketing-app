package io.callisto.ticketing.catalog;

import io.callisto.ticketing.domain.Event;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Deliberately in-memory — session 3's live coding swaps this for a real JpaRepository
// backed by Postgres. Method names mirror Spring Data's CrudRepository on purpose, so
// that swap changes the implementation, not the shape callers depend on.
@Repository
public class EventRepository {

	private final Map<String, Event> events = new ConcurrentHashMap<>();

	public Event save(Event event) {
		Event stored = event.getId() != null ? event : withGeneratedId(event);
		events.put(stored.getId(), stored);
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
		return event.toBuilder().id(UUID.randomUUID().toString()).build();
	}

}
