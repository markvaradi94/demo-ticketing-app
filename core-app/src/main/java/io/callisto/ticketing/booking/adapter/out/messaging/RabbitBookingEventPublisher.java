package io.callisto.ticketing.booking.adapter.out.messaging;

import io.callisto.ticketing.booking.application.port.out.BookingEventPublisherPort;
import io.callisto.ticketing.booking.domain.Booking;
import io.callisto.ticketing.messaging.BookingCancelledMessage;
import io.callisto.ticketing.messaging.BookingCreatedMessage;
import io.callisto.ticketing.messaging.BookingRoutingKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// Package-private, same reasoning as every other adapter — only the port needs to
// be public. Pure translation: Booking (domain) -> the shared message record ->
// publish to the exchange BookingEventsExchangeConfig declared back on
// session-07-start. No business logic lives here.
@Component
@RequiredArgsConstructor
class RabbitBookingEventPublisher implements BookingEventPublisherPort {

	private final RabbitTemplate rabbitTemplate;

	@Override
	public void publishCreated(Booking booking) {
		BookingCreatedMessage message = new BookingCreatedMessage(
				booking.getId(), booking.getEventId(), booking.getCustomerName(), booking.getSeatCount());
		rabbitTemplate.convertAndSend(BookingRoutingKeys.EXCHANGE, BookingRoutingKeys.CREATED, message);
	}

	@Override
	public void publishCancelled(Booking booking) {
		BookingCancelledMessage message = new BookingCancelledMessage(
				booking.getId(), booking.getEventId(), booking.getCustomerName());
		rabbitTemplate.convertAndSend(BookingRoutingKeys.EXCHANGE, BookingRoutingKeys.CANCELLED, message);
	}

}
