package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.booking.application.port.out.BookingRepositoryPort;
import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingId;
import io.callisto.ticketing.booking.domain.BookingStatus;
import io.callisto.ticketing.booking.domain.SeatCount;
import io.callisto.ticketing.catalog.EventNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test: {@link BookingService}'s business rules — too-many-seats, cancel-twice,
 * wrong-event scoping — with {@link BookingRepositoryPort}, {@link EventAvailabilityPort},
 * and {@link BookingProperties} all mocked, no Spring context or database. Mocking the
 * port rather than {@code EventRepository} directly means this test can no longer see
 * the actual {@code bookedSeats} increment — that logic now lives in
 * {@link io.callisto.ticketing.booking.adapter.out.catalog.EventAvailabilityAdapter},
 * proven by its own {@code EventAvailabilityAdapterTest}. This test only proves
 * {@link BookingService} calls the port correctly. For the HTTP contract see
 * {@link io.callisto.ticketing.booking.adapter.in.web.BookingControllerTest}; for the
 * full stack including the real increment, see
 * {@link io.callisto.ticketing.BookingJourneyIntegrationTest}.
 */
class BookingServiceTest {

	private static final Long EVENT_ID = 1L;

	private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
	private final EventAvailabilityPort events = mock(EventAvailabilityPort.class);
	private final BookingProperties bookingProperties = mock(BookingProperties.class);
	private final BookingService service = new BookingService(bookings, events, bookingProperties);

	@Test
	void createsABookingAndReservesSeatsThroughThePort() {
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);
		when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Booking created = service.create(unsavedBooking(EVENT_ID, "Ada Lovelace", 2));

		assertThat(created.getSeatCount()).isEqualTo(2);
		assertThat(created.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		verify(events).reserveSeats(EVENT_ID, SeatCount.of(2));
	}

	@Test
	void rejectsMoreSeatsThanAllowedWithoutEverCallingThePort() {
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);

		assertThatThrownBy(() -> service.create(unsavedBooking(EVENT_ID, "Margaret Hamilton", 50)))
				.isInstanceOf(TooManySeatsRequestedException.class);
		verify(events, never()).reserveSeats(any(), any());
	}

	@Test
	void rejectsBookingAnUnknownEvent() {
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);
		doThrow(new EventNotFoundException(999L)).when(events).reserveSeats(999L, SeatCount.of(1));

		assertThatThrownBy(() -> service.create(unsavedBooking(999L, "Alan Turing", 1)))
				.isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void rejectsFetchingABookingUnderTheWrongEvent() {
		Booking booking = Booking.builder().id(1L).eventId(EVENT_ID).customerName("Hedy Lamarr")
				.seatCount(1).status(BookingStatus.CONFIRMED).build();
		when(bookings.findById(BookingId.of(1L))).thenReturn(Optional.of(booking));

		assertThatThrownBy(() -> service.get(999L, BookingId.of(1L))).isInstanceOf(BookingNotFoundException.class);
	}

	@Test
	void cancelsAConfirmedBooking() {
		Booking booking = Booking.builder().id(1L).eventId(EVENT_ID).customerName("Grace Hopper")
				.seatCount(1).status(BookingStatus.CONFIRMED).build();
		when(bookings.findById(BookingId.of(1L))).thenReturn(Optional.of(booking));
		when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Booking cancelled = service.cancel(EVENT_ID, BookingId.of(1L));

		assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
	}

	@Test
	void rejectsCancellingAnAlreadyCancelledBooking() {
		Booking cancelled = Booking.builder().id(1L).eventId(EVENT_ID).customerName("Grace Hopper")
				.seatCount(1).status(BookingStatus.CANCELLED).build();
		when(bookings.findById(BookingId.of(1L))).thenReturn(Optional.of(cancelled));

		assertThatThrownBy(() -> service.cancel(EVENT_ID, BookingId.of(1L))).isInstanceOf(BookingAlreadyCancelledException.class);
	}

	private static Booking unsavedBooking(Long eventId, String customerName, int seatCount) {
		return Booking.builder().eventId(eventId).customerName(customerName)
				.seatCount(seatCount).status(BookingStatus.CONFIRMED).build();
	}

}
