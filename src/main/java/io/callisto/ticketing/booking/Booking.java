package io.callisto.ticketing.booking;

import io.callisto.ticketing.domain.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")
public class Booking {

	private String id;
	private String eventId;
	private String customerName;
	private int seatCount;
	private BookingStatus status;

}
