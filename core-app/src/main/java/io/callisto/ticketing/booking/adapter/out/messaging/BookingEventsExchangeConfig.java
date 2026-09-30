package io.callisto.ticketing.booking.adapter.out.messaging;

import io.callisto.ticketing.messaging.BookingRoutingKeys;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Declares the exchange only — core-app is the publisher's side of this topology.
// notification-service declares its own queue, binding, and dead-letter setup
// alongside the @RabbitListener that actually consumes from it; queues are a
// consumer-owned resource, not something the publisher should dictate.
// Nothing publishes to this exchange yet on this branch — that's this session's lab.
@Configuration
class BookingEventsExchangeConfig {

	@Bean
	TopicExchange bookingEventsExchange() {
		return new TopicExchange(BookingRoutingKeys.EXCHANGE);
	}

}
