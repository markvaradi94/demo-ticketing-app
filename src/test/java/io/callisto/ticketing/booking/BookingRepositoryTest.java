package io.callisto.ticketing.booking;

import io.callisto.ticketing.AbstractIntegrationTest;
import io.callisto.ticketing.domain.BookingStatus;
import io.callisto.ticketing.domain.Event;
import io.callisto.ticketing.domain.Venue;
import io.callisto.ticketing.catalog.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence-layer test: verifies {@link Booking}'s JPA mapping — including the
 * enum-to-string {@code status} column — against a real Postgres instance (via
 * {@link AbstractIntegrationTest}). No web layer involved. For the HTTP contract see
 * {@link BookingControllerTest}, for the full stack see
 * {@link io.callisto.ticketing.BookingJourneyIntegrationTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private BookingRepository bookings;

	@Autowired
	private EventRepository events;

	@Test
	void savesAndReloadsABookingWithItsStatus() {
		Event event = persistedEvent();
		Booking booking = Booking.builder().eventId(event.getId())
				.customerName("Ada Lovelace").seatCount(2).status(BookingStatus.CONFIRMED).build();

		Booking saved = bookings.save(booking);

		Optional<Booking> reloaded = bookings.findById(saved.getId());

		assertThat(reloaded).isPresent();
		assertThat(reloaded.get().getCustomerName()).isEqualTo("Ada Lovelace");
		assertThat(reloaded.get().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
	}

	@Test
	void returnsEmptyForAnUnknownId() {
		assertThat(bookings.findById(-1L)).isEmpty();
	}

	private Event persistedEvent() {
		Venue venue = Venue.builder().name("Blue Room").capacity(120).build();
		Event event = Event.builder().name("Jazz Night").venue(venue).startTime(Instant.now()).build();
		return events.save(event);
	}

}
