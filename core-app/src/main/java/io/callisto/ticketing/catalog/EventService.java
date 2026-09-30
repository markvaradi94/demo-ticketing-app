package io.callisto.ticketing.catalog;

import io.callisto.ticketing.catalog.dto.EventRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Package-private — EventController is the only caller within this module; no other
// module should be calling a service directly, EventClient is the sanctioned way in
// from outside catalog. Same controller/service/repository + mapper shape already
// applied to booking and review — this is that same pattern, live-coded here.
@Service
@RequiredArgsConstructor
class EventService {

	private final EventRepository events;

	Event create(EventRequest request) {
		return events.save(EventMapper.toNewEvent(request));
	}

	List<Event> list() {
		return events.findAll();
	}

	Event get(Long id) {
		return events.findById(id).orElseThrow(() -> new EventNotFoundException(id));
	}

	Event update(Long id, EventRequest request) {
		Event existing = get(id);
		return events.save(EventMapper.applyUpdate(existing, request));
	}

	void delete(Long id) {
		get(id);
		events.deleteById(id);
	}

}
