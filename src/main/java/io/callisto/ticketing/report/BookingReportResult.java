package io.callisto.ticketing.report;

import java.util.HashMap;
import java.util.Map;

public class BookingReportResult {

	private int totalBookings;
	private int totalSeatsSold;
	private double totalRevenue;
	private int cancelledCount;
	private Map<String, Double> revenueByEvent = new HashMap<>();

	public int getTotalBookings() {
		return totalBookings;
	}

	public void setTotalBookings(int totalBookings) {
		this.totalBookings = totalBookings;
	}

	public int getTotalSeatsSold() {
		return totalSeatsSold;
	}

	public void setTotalSeatsSold(int totalSeatsSold) {
		this.totalSeatsSold = totalSeatsSold;
	}

	public double getTotalRevenue() {
		return totalRevenue;
	}

	public void setTotalRevenue(double totalRevenue) {
		this.totalRevenue = totalRevenue;
	}

	public int getCancelledCount() {
		return cancelledCount;
	}

	public void setCancelledCount(int cancelledCount) {
		this.cancelledCount = cancelledCount;
	}

	public Map<String, Double> getRevenueByEvent() {
		return revenueByEvent;
	}

	public void setRevenueByEvent(Map<String, Double> revenueByEvent) {
		this.revenueByEvent = revenueByEvent;
	}

}
