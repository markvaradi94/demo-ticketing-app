package io.callisto.ticketing.catalog;

import io.callisto.ticketing.catalog.dto.EventRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test: {@link EventService}'s business rules — not-found handling, and the
 * update()-preserves-id-and-version shape — with {@link EventRepository} mocked, no
 * Spring context or database. This is the layer that used to be tested inside
 * {@link EventControllerTest}, back when the controller called the repository
 * directly; now that test only proves the HTTP shape, and this proves the rules. For
 * proof the version-preserving update genuinely works against real Postgres, see
 * {@link EventRepositoryTest} and {@link io.callisto.ticketing.EventJourneyIntegrationTest}.
 */
class EventServiceTest {

	private final EventRepository events = mock(EventRepository.class);
	private final EventService service = new EventService(events);

	@Test
	void createsAnEventFromTheRequest() {
		EventRequest request = new EventRequest("Jazz Night", "Blue Room", 120, new BigDecimal("25.00"), Instant.now().plus(30, ChronoUnit.DAYS));
		when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Event created = service.create(request);

		assertThat(created.getName()).isEqualTo("Jazz Night");
		assertThat(created.getVenue().getName()).isEqualTo("Blue Room");
		assertThat(created.getVenue().getCapacity()).isEqualTo(120);
	}

	@Test
	void listsAllEvents() {
		Event event = stubEvent(1L);
		when(events.findAll()).thenReturn(List.of(event));

		assertThat(service.list()).containsExactly(event);
	}

	@Test
	void throwsWhenGettingAnUnknownEvent() {
		when(events.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(999L)).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void updatePreservesIdAndVersionFromTheLoadedEntity() {
		Event existing = Event.builder().id(1L).name("Jazz Night")
				.venue(Venue.builder().name("Blue Room").capacity(120).build())
				.startTime(Instant.now()).version(3L).build();
		when(events.findById(1L)).thenReturn(Optional.of(existing));
		when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

		EventRequest request = new EventRequest("Jazz Night (Rescheduled)", "Blue Room", 150, new BigDecimal("30.00"), Instant.now().plus(45, ChronoUnit.DAYS));
		Event updated = service.update(1L, request);

		assertThat(updated.getId()).isEqualTo(1L);
		assertThat(updated.getVersion()).isEqualTo(3L); // the whole point of toBuilder() over a fresh builder()
		assertThat(updated.getName()).isEqualTo("Jazz Night (Rescheduled)");
		assertThat(updated.getVenue().getCapacity()).isEqualTo(150);
	}

	@Test
	void throwsWhenUpdatingAnUnknownEvent() {
		when(events.findById(999L)).thenReturn(Optional.empty());

		EventRequest request = new EventRequest("Jazz Night", "Blue Room", 120, new BigDecimal("25.00"), Instant.now().plus(30, ChronoUnit.DAYS));
		assertThatThrownBy(() -> service.update(999L, request)).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void deletesAnExistingEvent() {
		when(events.findById(1L)).thenReturn(Optional.of(stubEvent(1L)));

		service.delete(1L);

		verify(events).deleteById(1L);
	}

	@Test
	void throwsWhenDeletingAnUnknownEvent() {
		when(events.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(999L)).isInstanceOf(EventNotFoundException.class);
	}

	private static Event stubEvent(Long id) {
		Venue venue = Venue.builder().id(1L).name("Blue Room").capacity(120).build();
		return Event.builder().id(id).name("Jazz Night").venue(venue).startTime(Instant.now()).build();
	}

}
