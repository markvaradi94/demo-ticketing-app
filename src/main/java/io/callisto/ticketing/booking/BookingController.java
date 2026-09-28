package io.callisto.ticketing.booking;

import io.callisto.ticketing.booking.dto.BookingRequest;
import io.callisto.ticketing.booking.dto.BookingResponse;
import io.callisto.ticketing.catalog.EventNotFoundException;
import io.callisto.ticketing.catalog.EventRepository;
import io.callisto.ticketing.domain.BookingStatus;
import io.callisto.ticketing.domain.Event;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/events/{eventId}/bookings")
@RequiredArgsConstructor
public class BookingController {

	private final BookingRepository bookings;
	private final EventRepository events;
	private final BookingProperties bookingProperties;

	@PostMapping
	public ResponseEntity<BookingResponse> create(@PathVariable Long eventId, @Valid @RequestBody BookingRequest request) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));

		if (request.seatCount() > bookingProperties.maxSeatsPerBooking()) {
			throw new TooManySeatsRequestedException(request.seatCount(), bookingProperties.maxSeatsPerBooking());
		}

		events.save(event.withBookedSeats(event.getBookedSeats() + request.seatCount()));

		Booking booking = Booking.builder()
				.eventId(eventId)
				.customerName(request.customerName())
				.seatCount(request.seatCount())
				.status(new BookingStatus.Confirmed())
				.build();
		Booking saved = bookings.save(booking);
		URI location = URI.create("/events/" + eventId + "/bookings/" + saved.getId());
		return ResponseEntity.created(location).body(toResponse(saved));
	}

	@GetMapping("/{bookingId}")
	public BookingResponse get(@PathVariable Long eventId, @PathVariable String bookingId) {
		return toResponse(findOrThrow(eventId, bookingId));
	}

	@PostMapping("/{bookingId}/cancel")
	public BookingResponse cancel(@PathVariable Long eventId, @PathVariable String bookingId) {
		Booking booking = findOrThrow(eventId, bookingId);
		if (booking.getStatus() instanceof BookingStatus.Cancelled) {
			throw new BookingAlreadyCancelledException(bookingId);
		}
		Booking cancelled = booking.toBuilder().status(new BookingStatus.Cancelled()).build();
		return toResponse(bookings.save(cancelled));
	}

	private Booking findOrThrow(Long eventId, String bookingId) {
		Booking booking = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
		if (!booking.getEventId().equals(eventId)) {
			throw new BookingNotFoundException(bookingId);
		}
		return booking;
	}

	private static BookingResponse toResponse(Booking booking) {
		return new BookingResponse(booking.getId(), booking.getEventId(), booking.getCustomerName(), booking.getSeatCount(), statusName(booking.getStatus()));
	}

	private static String statusName(BookingStatus status) {
		return switch (status) {
			case BookingStatus.Pending ignored -> "PENDING";
			case BookingStatus.Confirmed ignored -> "CONFIRMED";
			case BookingStatus.Cancelled ignored -> "CANCELLED";
		};
	}

}
