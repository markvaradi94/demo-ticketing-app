package io.callisto.notificationservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingEventsListenerTest {

	private final BookingEventsListener listener = new BookingEventsListener();

	@Test
	void treatsTheFirstDeliveryOfAnIdAsNew() {
		assertThat(listener.alreadyProcessed(1L)).isFalse();
	}

	@Test
	void treatsARepeatedIdAsAlreadyProcessed() {
		listener.alreadyProcessed(1L);

		assertThat(listener.alreadyProcessed(1L)).isTrue();
	}

	@Test
	void tracksEachBookingIdIndependently() {
		listener.alreadyProcessed(1L);

		assertThat(listener.alreadyProcessed(2L)).isFalse();
	}

}
