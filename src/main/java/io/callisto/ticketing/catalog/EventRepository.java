package io.callisto.ticketing.catalog;

import io.callisto.ticketing.domain.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

	// Fixes the N+1: EventController.list() reads event.getVenue() for every event,
	// and Venue is lazy — without this, that's one SELECT per event on top of the
	// findAll() itself. @EntityGraph joins Venue into the same query.
	@EntityGraph(attributePaths = "venue")
	@Override
	List<Event> findAll();

}
