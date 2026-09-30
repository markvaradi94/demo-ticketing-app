package io.callisto.ticketing.booking.domain;

import io.callisto.ticketing.booking.OverbookingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The overbooking-impossible proof: a fast, deterministic domain-level test, not a
 * concurrent-threads test racing against real timing. Session 3's
 * {@code EventRepositoryTest.rejectsASecondSaveAgainstAStaleVersion()} already covers
 * the race-protection half (two concurrent writes can't silently clobber each other);
 * this covers the half that was missing entirely before this session — a capacity
 * ceiling that actually exists.
 */
class EventInventoryTest {

	@Test
	void reservesSeatsWhenCapacityAllows() {
		EventInventory inventory = EventInventory.of(1L, 100, 90);

		EventInventory updated = inventory.reserve(SeatCount.of(10));

		assertThat(updated.bookedSeats()).isEqualTo(100);
	}

	@Test
	void refusesToExceedCapacity() {
		EventInventory inventory = EventInventory.of(1L, 100, 95);

		assertThatThrownBy(() -> inventory.reserve(SeatCount.of(10)))
				.isInstanceOf(OverbookingException.class);
	}

	@Test
	void releaseFreesUpCapacity() {
		EventInventory inventory = EventInventory.of(1L, 100, 100);

		EventInventory updated = inventory.release(SeatCount.of(20));

		assertThat(updated.bookedSeats()).isEqualTo(80);
	}

	@Test
	void releaseNeverGoesBelowZero() {
		EventInventory inventory = EventInventory.of(1L, 100, 3);

		EventInventory updated = inventory.release(SeatCount.of(10));

		assertThat(updated.bookedSeats()).isZero();
	}

}
