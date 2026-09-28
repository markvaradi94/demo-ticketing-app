package io.callisto.ticketing.catalog;

import io.callisto.ticketing.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, String> {
}
