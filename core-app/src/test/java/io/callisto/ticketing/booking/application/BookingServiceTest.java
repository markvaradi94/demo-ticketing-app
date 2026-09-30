package io.callisto.ticketing.booking.application;

import io.callisto.ticketing.booking.BookingAlreadyCancelledException;
import io.callisto.ticketing.booking.BookingNotFoundException;
import io.callisto.ticketing.booking.PaymentDeclinedException;
import io.callisto.ticketing.booking.TooManySeatsRequestedException;
import io.callisto.ticketing.booking.application.port.out.BookingEventPublisherPort;
import io.callisto.ticketing.booking.application.port.out.BookingRepositoryPort;
import io.callisto.ticketing.booking.application.port.out.EventAvailabilityPort;
import io.callisto.ticketing.booking.application.port.out.PaymentPort;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.booking.domain.BookingCancelled;
import io.callisto.ticketing.booking.domain.BookingId;
import io.callisto.ticketing.booking.domain.BookingStatus;
import io.callisto.ticketing.booking.domain.SeatCount;
import io.callisto.ticketing.catalog.EventClient;
import io.callisto.ticketing.catalog.EventNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test: {@link BookingService}'s business rules — too-many-seats, cancel-twice,
 * wrong-event scoping, the payment-decline-releases-seats compensation — with
 * {@link BookingRepositoryPort}, {@link EventAvailabilityPort}, {@link PaymentPort},
 * {@link EventClient}, and {@link BookingProperties} all mocked, no Spring context or
 * database. Mocking the ports rather than the real repositories/RestClient means this
 * test can't see the actual {@code bookedSeats} increment or a real HTTP call to
 * payment-service — those are {@code EventAvailabilityAdapterTest}/
 * {@code PaymentAdapterTest}'s job. This test only proves {@link BookingService}
 * calls its collaborators correctly, in the right order. For the HTTP contract see
 * {@link io.callisto.ticketing.booking.adapter.in.web.BookingControllerTest}.
 */
class BookingServiceTest {

	private static final Long EVENT_ID = 1L;

	private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
	private final EventAvailabilityPort events = mock(EventAvailabilityPort.class);
	private final PaymentPort payments = mock(PaymentPort.class);
	private final BookingEventPublisherPort bookingEvents = mock(BookingEventPublisherPort.class);
	private final EventClient eventClient = mock(EventClient.class);
	private final BookingProperties bookingProperties = mock(BookingProperties.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final BookingService service = new BookingService(bookings, events, payments, bookingEvents, eventClient, bookingProperties, eventPublisher);

	@Test
	void createsABookingAndReservesSeatsThroughThePort() {
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);
		when(eventClient.pricePerSeat(EVENT_ID)).thenReturn(new BigDecimal("25.00"));
		when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Booking created = service.create(unsavedBooking(EVENT_ID, "Ada Lovelace", 2));

		assertThat(created.getSeatCount()).isEqualTo(2);
		assertThat(created.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		verify(events).reserveSeats(EVENT_ID, SeatCount.of(2));
		verify(bookingEvents).publishCreated(created);
	}

	@Test
	void chargesSeatCountTimesPricePerSeat() {
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);
		when(eventClient.pricePerSeat(EVENT_ID)).thenReturn(new BigDecimal("25.00"));
		when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.create(unsavedBooking(EVENT_ID, "Ada Lovelace", 3));

		verify(payments).charge(anyString(), eq(new BigDecimal("75.00")));
	}

	@Test
	void rejectsMoreSeatsThanAllowedWithoutEverCallingThePort() {
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);

		assertThatThrownBy(() -> service.create(unsavedBooking(EVENT_ID, "Margaret Hamilton", 50)))
				.isInstanceOf(TooManySeatsRequestedException.class);
		verify(events, never()).reserveSeats(any(), any());
		verify(payments, never()).charge(any(), any());
	}

	@Test
	void rejectsBookingAnUnknownEvent() {
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);
		doThrow(new EventNotFoundException(999L)).when(events).reserveSeats(999L, SeatCount.of(1));

		assertThatThrownBy(() -> service.create(unsavedBooking(999L, "Alan Turing", 1)))
				.isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void releasesTheReservedSeatsWhenPaymentIsDeclined() {
		when(bookingProperties.maxSeatsPerBooking()).thenReturn(8);
		when(eventClient.pricePerSeat(EVENT_ID)).thenReturn(new BigDecimal("25.00"));
		doThrow(new PaymentDeclinedException("ref")).when(payments).charge(anyString(), any(BigDecimal.class));

		assertThatThrownBy(() -> service.create(unsavedBooking(EVENT_ID, "Margaret Hamilton", 2)))
				.isInstanceOf(PaymentDeclinedException.class);

		verify(events).releaseSeats(EVENT_ID, SeatCount.of(2));
		verify(bookings, never()).save(any());
		verify(bookingEvents, never()).publishCreated(any());
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
		verify(eventPublisher).publishEvent(new BookingCancelled(EVENT_ID, SeatCount.of(1)));
		verify(bookingEvents).publishCancelled(cancelled);
	}

	@Test
	void rejectsCancellingAnAlreadyCancelledBooking() {
		Booking cancelled = Booking.builder().id(1L).eventId(EVENT_ID).customerName("Grace Hopper")
				.seatCount(1).status(BookingStatus.CANCELLED).build();
		when(bookings.findById(BookingId.of(1L))).thenReturn(Optional.of(cancelled));

		assertThatThrownBy(() -> service.cancel(EVENT_ID, BookingId.of(1L))).isInstanceOf(BookingAlreadyCancelledException.class);
		verify(eventPublisher, never()).publishEvent(any());
		verify(bookingEvents, never()).publishCancelled(any());
	}

	private static Booking unsavedBooking(Long eventId, String customerName, int seatCount) {
		return Booking.builder().eventId(eventId).customerName(customerName)
				.seatCount(seatCount).status(BookingStatus.CONFIRMED).build();
	}

}
