package io.callisto.notificationservice;

import io.callisto.ticketing.messaging.BookingRoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Declares this service's own queues and bindings — the exchange itself is
// core-app's to own (BookingEventsExchangeConfig on session-07-start), but a queue
// is a consumer-owned resource: this service decides what it wants and how.
// Re-declaring the same-named exchange here is normal AMQP practice, not
// duplication to clean up — every service that binds to an exchange declares it,
// and RabbitMQ no-ops as long as the properties agree.
//
// Two queues, one per message shape, rather than one queue carrying both types —
// each @RabbitListener method below expects exactly one Java type, and mixing two
// shapes on one queue would mean two listeners competing for the same messages
// instead of each owning its own.
@Configuration
class BookingEventsQueueConfig {

	private static final String DEAD_LETTER_EXCHANGE = "notification.booking-events.dlx";
	private static final String CREATED_DEAD_LETTER_QUEUE = "notification.booking-created.dlq";

	@Bean
	TopicExchange bookingEventsExchange() {
		return new TopicExchange(BookingRoutingKeys.EXCHANGE);
	}

	// A message this service can never successfully process (malformed JSON, or the
	// listener itself throwing) gets rejected without requeue — see
	// spring.rabbitmq.listener.simple.default-requeue-rejected=false — and lands
	// here instead of looping forever. Only wired for the created queue this
	// session; the same pattern applies to the cancelled queue too, just not
	// repeated here for lab time.
	@Bean
	DirectExchange bookingEventsDeadLetterExchange() {
		return new DirectExchange(DEAD_LETTER_EXCHANGE);
	}

	@Bean
	Queue bookingCreatedDeadLetterQueue() {
		return new Queue(CREATED_DEAD_LETTER_QUEUE, true);
	}

	@Bean
	Binding bookingCreatedDeadLetterBinding(Queue bookingCreatedDeadLetterQueue, DirectExchange bookingEventsDeadLetterExchange) {
		return BindingBuilder.bind(bookingCreatedDeadLetterQueue).to(bookingEventsDeadLetterExchange).with(CREATED_DEAD_LETTER_QUEUE);
	}

	@Bean
	Queue bookingCreatedQueue() {
		return QueueBuilder.durable("notification.booking-created")
				.deadLetterExchange(DEAD_LETTER_EXCHANGE)
				.deadLetterRoutingKey(CREATED_DEAD_LETTER_QUEUE)
				.build();
	}

	@Bean
	Queue bookingCancelledQueue() {
		return new Queue("notification.booking-cancelled", true);
	}

	@Bean
	Binding bookingCreatedBinding(Queue bookingCreatedQueue, TopicExchange bookingEventsExchange) {
		return BindingBuilder.bind(bookingCreatedQueue).to(bookingEventsExchange).with(BookingRoutingKeys.CREATED);
	}

	@Bean
	Binding bookingCancelledBinding(Queue bookingCancelledQueue, TopicExchange bookingEventsExchange) {
		return BindingBuilder.bind(bookingCancelledQueue).to(bookingEventsExchange).with(BookingRoutingKeys.CANCELLED);
	}

	@Bean
	MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}

}
