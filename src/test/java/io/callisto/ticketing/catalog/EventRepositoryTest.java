package io.callisto.ticketing.catalog;

import io.callisto.ticketing.AbstractIntegrationTest;
import io.callisto.ticketing.domain.Event;
import io.callisto.ticketing.domain.Venue;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void fetchesAllEventsWithTheirVenuesInOneQuery() {
		Event first = events.save(newEventAt("Blue Room"));
		Event second = events.save(newEventAt("Attic"));
		events.flush();

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		List<Event> all = events.findAll();
		all.forEach(Event::getVenue); // mirrors EventController.list() touching the lazy venue

		// findAll() isn't scoped to this test's data — the container is shared with
		// integration tests hitting the same table over real HTTP — so filter down to
		// what this test actually created rather than asserting on the whole table.
		List<Event> ours = all.stream().filter(e -> e.getId().equals(first.getId()) || e.getId().equals(second.getId())).toList();
		assertThat(ours).hasSize(2);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	// @DataJpaTest wraps every test method in one transaction/persistence context by
	// default, so two "concurrent" save() calls in the same method would actually
	// share one identity map and never race. NOT_SUPPORTED forces each repository
	// call onto its own transaction, genuinely reproducing two separate requests —
	// but that also means this method's writes are real commits against the shared
	// container, not rolled back like every other test here, so it cleans up after
	// itself explicitly.
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void rejectsASecondSaveAgainstAStaleVersion() {
		Event saved = events.save(newEventAt("Blue Room"));

		Event firstRead = events.findById(saved.getId()).orElseThrow();
		Event secondRead = events.findById(saved.getId()).orElseThrow();

		events.save(firstRead.withBookedSeats(2));

		assertThatThrownBy(() -> events.save(secondRead.withBookedSeats(3)))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);

		events.deleteById(saved.getId());
	}

	@Test
	void savesAndReloadsAnEventWithItsVenue() {
		Event saved = events.save(newEventAt("Blue Room"));

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

	private static Event newEventAt(String venueName) {
		// No id set — generated via the identity column on save, same as every other
		// relational entity in this app.
		Venue venue = Venue.builder().name(venueName).capacity(120).build();
		return Event.builder().name("Jazz Night").venue(venue).startTime(Instant.now()).build();
	}

}
