package io.callisto.ticketing.booking.adapter.out.catalog;

import io.callisto.ticketing.catalog.Event;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
import io.callisto.ticketing.catalog.Venue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test: {@link EventAvailabilityAdapter}'s actual logic — read, increment,
 * save — with {@link EventRepository} mocked, no Spring context. Narrow enough that
 * it would be tempting to skip, but it's genuine logic, not a pass-through, same
 * reasoning session 4 applied to {@code EventClientTest}.
 */
class EventAvailabilityAdapterTest {

	private static final Long EVENT_ID = 1L;

	private final EventRepository events = mock(EventRepository.class);
	private final EventAvailabilityAdapter adapter = new EventAvailabilityAdapter(events);

	@Test
	void reservesSeatsByIncrementingBookedSeats() {
		when(events.findById(EVENT_ID)).thenReturn(Optional.of(stubEvent(3)));

		adapter.reserveSeats(EVENT_ID, 2);

		verify(events).save(argThat(event -> event.getBookedSeats() == 5));
	}

	@Test
	void throwsWhenTheEventDoesNotExist() {
		when(events.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> adapter.reserveSeats(999L, 1)).isInstanceOf(EventNotFoundException.class);
	}

	private static Event stubEvent(int bookedSeats) {
		Venue venue = Venue.builder().id(1L).name("Blue Room").capacity(120).build();
		return Event.builder().id(EVENT_ID).name("Jazz Night").venue(venue)
				.startTime(Instant.now()).bookedSeats(bookedSeats).build();
	}

}
