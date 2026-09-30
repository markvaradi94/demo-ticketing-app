package io.callisto.ticketing.booking.adapter.out.messaging;

import io.callisto.ticketing.messaging.BookingRoutingKeys;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Declares the exchange — core-app is the publisher's side of this topology.
// notification-service declares its own queue, binding, and dead-letter setup
// alongside the @RabbitListener that actually consumes from it; queues are a
// consumer-owned resource, not something the publisher should dictate.
//
// The message converter matters as much as the exchange: Spring AMQP's default
// SimpleMessageConverter falls back to JDK serialization for anything that isn't a
// String/byte[], which nothing on the consuming side could realistically decode.
// JacksonJsonMessageConverter (Jackson 3's replacement for the now-deprecated
// Jackson2JsonMessageConverter, matching this Boot version) publishes real JSON —
// Spring Boot's autoconfigured RabbitTemplate picks up this bean automatically.
@Configuration
class BookingEventsExchangeConfig {

	@Bean
	TopicExchange bookingEventsExchange() {
		return new TopicExchange(BookingRoutingKeys.EXCHANGE);
	}

	@Bean
	MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}

}
