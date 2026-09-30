package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.domain.BookingCancelled;
import io.callisto.ticketing.booking.domain.SeatCount;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookingCancelledEventListenerTest {

	private final EventAvailabilityPort events = mock(EventAvailabilityPort.class);
	private final BookingCancelledEventListener listener = new BookingCancelledEventListener(events);

	@Test
	void releasesTheCancelledBookingsSeats() {
		listener.onBookingCancelled(new BookingCancelled(1L, SeatCount.of(2)));

		verify(events).releaseSeats(1L, SeatCount.of(2));
	}

}
