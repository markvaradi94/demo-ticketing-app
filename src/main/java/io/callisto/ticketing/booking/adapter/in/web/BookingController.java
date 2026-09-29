package io.callisto.ticketing.booking.adapter.in.web;

import io.callisto.ticketing.booking.adapter.in.web.dto.BookingRequest;
import io.callisto.ticketing.booking.adapter.in.web.dto.BookingResponse;
import io.callisto.ticketing.booking.application.BookingService;
import io.callisto.ticketing.booking.domain.Booking;
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

	private final BookingService bookings;

	@PostMapping
	public ResponseEntity<BookingResponse> create(@PathVariable Long eventId, @Valid @RequestBody BookingRequest request) {
		Booking saved = bookings.create(BookingMapper.toNewBooking(eventId, request));
		URI location = URI.create("/events/" + eventId + "/bookings/" + saved.getId());
		return ResponseEntity.created(location).body(BookingMapper.toResponse(saved));
	}

	@GetMapping("/{bookingId}")
	public BookingResponse get(@PathVariable Long eventId, @PathVariable Long bookingId) {
		return BookingMapper.toResponse(bookings.get(eventId, bookingId));
	}

	@PostMapping("/{bookingId}/cancel")
	public BookingResponse cancel(@PathVariable Long eventId, @PathVariable Long bookingId) {
		return BookingMapper.toResponse(bookings.cancel(eventId, bookingId));
	}

}
