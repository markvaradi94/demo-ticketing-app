package io.callisto.ticketing.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(name = "events")
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinColumn(name = "venue_id")
	private Venue venue;

	private Instant startTime;

	@Builder.Default
	private int bookedSeats = 0;

	// Fixes the lost update: two concurrent bookedSeats writes on the same row now
	// race on this instead of silently overwriting each other — the second save()
	// throws ObjectOptimisticLockingFailureException, mapped to 409 by
	// GlobalExceptionHandler. Left null on new entities; Hibernate seeds it on insert.
	@Version
	private Long version;

	public Event withBookedSeats(int newBookedSeats) {
		return toBuilder().bookedSeats(newBookedSeats).build();
	}

}
