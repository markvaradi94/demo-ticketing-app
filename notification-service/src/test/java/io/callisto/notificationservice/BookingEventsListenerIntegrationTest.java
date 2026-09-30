package io.callisto.notificationservice;

import io.callisto.ticketing.messaging.BookingCreatedMessage;
import io.callisto.ticketing.messaging.BookingRoutingKeys;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test in this session proving the actual RabbitMQ plumbing works — a real
 * broker, the real exchange/queue/binding declarations from
 * {@link BookingEventsQueueConfig}, real JSON conversion — not just that the code
 * compiles. Publishes through the same exchange and routing key core-app uses and
 * confirms {@link BookingEventsListener} genuinely received and processed it, not
 * that it merely could in theory. Container lifecycle managed the same way
 * {@code AbstractIntegrationTest} does in core-app — a manual static block, not the
 * {@code @Testcontainers}/{@code @Container} JUnit 5 extension, which would need
 * its own separate dependency.
 */
@SpringBootTest
class BookingEventsListenerIntegrationTest {

	@ServiceConnection
	static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));

	static {
		RABBITMQ.start();
	}

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private BookingEventsListener listener;

	@Test
	void receivesAndProcessesARealBookingCreatedMessage() throws InterruptedException {
		BookingCreatedMessage message = new BookingCreatedMessage(42L, 1L, "Ada Lovelace", 2);

		rabbitTemplate.convertAndSend(BookingRoutingKeys.EXCHANGE, BookingRoutingKeys.CREATED, message);

		long deadline = System.currentTimeMillis() + 5000;
		while (!listener.hasBeenProcessed(42L) && System.currentTimeMillis() < deadline) {
			Thread.sleep(100);
		}

		assertThat(listener.hasBeenProcessed(42L)).isTrue();
	}

}
