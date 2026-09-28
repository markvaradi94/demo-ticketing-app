package io.callisto.ticketing.catalog;

import io.callisto.ticketing.AbstractIntegrationTest;
import io.callisto.ticketing.domain.Event;
import io.callisto.ticketing.domain.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence-layer test: verifies JPA mapping and repository behavior against a real
 * Postgres instance (via {@link AbstractIntegrationTest}). No web layer involved —
 * {@code @DataJpaTest} boots only the JPA slice, and each test runs inside a
 * transaction that's rolled back afterward, so tests don't need to clean up after
 * themselves.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private EventRepository events;

	@Test
	void savesAndReloadsAnEventWithItsVenue() {
		Venue venue = Venue.builder().name("Blue Room").capacity(120).build();
		Event event = Event.builder().name("Jazz Night").venue(venue).startTime(Instant.now()).build();

		Event saved = events.save(event);

		Optional<Event> reloaded = events.findById(saved.getId());

		assertThat(reloaded).isPresent();
		assertThat(reloaded.get().getName()).isEqualTo("Jazz Night");
		assertThat(reloaded.get().getVenue().getName()).isEqualTo("Blue Room");
		assertThat(reloaded.get().getVenue().getCapacity()).isEqualTo(120);
	}

	@Test
	void returnsEmptyForAnUnknownId() {
		assertThat(events.findById(-1L)).isEmpty();
	}

}
