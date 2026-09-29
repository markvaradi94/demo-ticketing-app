package io.callisto.ticketing.catalog;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit test: {@link EventClient}'s only logic is "look up, map to a name, throw if
 * missing" — light enough that a mocked {@link EventRepository} is the right tool, no
 * Spring context or database needed. For why this class exists at all instead of
 * other modules depending on {@link EventRepository} directly, see its own comment.
 */
class EventClientTest {

	private final EventRepository events = Mockito.mock(EventRepository.class);
	private final EventClient client = new EventClient(events);

	@Test
	void returnsTheEventsName() {
		Event event = Event.builder().id(1L).name("Jazz Night").build();
		when(events.findById(1L)).thenReturn(Optional.of(event));

		assertThat(client.nameOf(1L)).isEqualTo("Jazz Night");
	}

	@Test
	void throwsWhenTheEventDoesNotExist() {
		when(events.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> client.nameOf(999L)).isInstanceOf(EventNotFoundException.class);
	}

}
