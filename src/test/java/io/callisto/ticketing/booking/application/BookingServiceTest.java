package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.booking.adapter.out.persistence.BookingRepository;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingStatus;
import io.callisto.ticketing.catalog.Event;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
import io.callisto.ticketing.catalog.Venue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test: {@link BookingService}'s business rules — too-many-seats,
 * cancel-twice, wrong-event scoping, and the {@code bookedSeats} increment — with
 * {@link BookingRepository}, {@link EventRepository}, and {@link BookingProperties}
 * all mocked, no Spring context or database. {@link BookingService#create} takes an
 * already-built {@link Booking}, not a request DTO — the adapter owns that mapping now,
 * so this test builds the domain object directly the same way the controller's mapper
 * does. For the HTTP contract see
 * {@link io.callisto.ticketing.booking.adapter.in.web.BookingControllerTest}; for proof
 * the increment genuinely survives a race against real Postgres, see
 * {@code EventRepositoryTest.rejectsASecondSaveAgainstAStaleVersion()}.
 */
class BookingServiceTest {

	private static final Long EVENT_ID = 1L;

	private final BookingRepository bookings = mock(BookingRepository.class);
	private final EventRepository events = mock(EventRepository.class);
	private final BookingProperties bookingProperties = mock(BookingProperties.class);
	private final BookingService service = new BookingService(bookings, events, bookingProperties);

	@Test
	void createsABookingAndIncrementsTheEventsBookedSeats() {
		when(events.findById(EVENT_ID)).thenReturn(Optional.of(stubEvent(0)));
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);
		when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Booking created = service.create(unsavedBooking(EVENT_ID, "Ada Lovelace", 2));

		assertThat(created.getSeatCount()).isEqualTo(2);
		assertThat(created.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		verify(events).save(argThatBookedSeatsEquals(2));
	}

	@Test
	void rejectsMoreSeatsThanAllowed() {
		when(events.findById(EVENT_ID)).thenReturn(Optional.of(stubEvent(0)));
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);

		assertThatThrownBy(() -> service.create(unsavedBooking(EVENT_ID, "Margaret Hamilton", 50)))
				.isInstanceOf(TooManySeatsRequestedException.class);
	}

	@Test
	void rejectsBookingAnUnknownEvent() {
		when(events.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(unsavedBooking(999L, "Alan Turing", 1)))
				.isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void rejectsFetchingABookingUnderTheWrongEvent() {
		Booking booking = Booking.builder().id(1L).eventId(EVENT_ID).customerName("Hedy Lamarr")
				.seatCount(1).status(BookingStatus.CONFIRMED).build();
		when(bookings.findById(1L)).thenReturn(Optional.of(booking));

		assertThatThrownBy(() -> service.get(999L, 1L)).isInstanceOf(BookingNotFoundException.class);
	}

	@Test
	void cancelsAConfirmedBooking() {
		Booking booking = Booking.builder().id(1L).eventId(EVENT_ID).customerName("Grace Hopper")
				.seatCount(1).status(BookingStatus.CONFIRMED).build();
		when(bookings.findById(1L)).thenReturn(Optional.of(booking));
		when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Booking cancelled = service.cancel(EVENT_ID, 1L);

		assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
	}

	@Test
	void rejectsCancellingAnAlreadyCancelledBooking() {
		Booking cancelled = Booking.builder().id(1L).eventId(EVENT_ID).customerName("Grace Hopper")
				.seatCount(1).status(BookingStatus.CANCELLED).build();
		when(bookings.findById(1L)).thenReturn(Optional.of(cancelled));

		assertThatThrownBy(() -> service.cancel(EVENT_ID, 1L)).isInstanceOf(BookingAlreadyCancelledException.class);
	}

	private static Booking unsavedBooking(Long eventId, String customerName, int seatCount) {
		return Booking.builder().eventId(eventId).customerName(customerName)
				.seatCount(seatCount).status(BookingStatus.CONFIRMED).build();
	}

	private static Event stubEvent(int bookedSeats) {
		Venue venue = Venue.builder().id(1L).name("Blue Room").capacity(120).build();
		return Event.builder().id(EVENT_ID).name("Jazz Night").venue(venue)
				.startTime(Instant.now()).bookedSeats(bookedSeats).build();
	}

	private static Event argThatBookedSeatsEquals(int expected) {
		return org.mockito.ArgumentMatchers.argThat(event -> event.getBookedSeats() == expected);
	}

}
