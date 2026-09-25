package io.callisto.ticketing.report;

import java.util.List;

public class LegacyBooking {

	private String eventId;
	private String status;
	private List<String> seatLabels;
	private double pricePerSeat;

	public LegacyBooking() {
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public List<String> getSeatLabels() {
		return seatLabels;
	}

	public void setSeatLabels(List<String> seatLabels) {
		this.seatLabels = seatLabels;
	}

	public double getPricePerSeat() {
		return pricePerSeat;
	}

	public void setPricePerSeat(double pricePerSeat) {
		this.pricePerSeat = pricePerSeat;
	}

}
