package io.callisto.ticketing.booking;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Deliberately in-memory — session 3's live coding swaps this for a real JpaRepository
// backed by Postgres. Method names mirror Spring Data's CrudRepository on purpose, so
// that swap changes the implementation, not the shape callers depend on.
@Repository
public class BookingRepository {

	private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

	public Booking save(Booking booking) {
		Booking stored = booking.getId() != null ? booking : withGeneratedId(booking);
		bookings.put(stored.getId(), stored);
		return stored;
	}

	public Optional<Booking> findById(String id) {
		return Optional.ofNullable(bookings.get(id));
	}

	private Booking withGeneratedId(Booking booking) {
		return booking.toBuilder().id(UUID.randomUUID().toString()).build();
	}

}
