package io.callisto.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// No @RabbitListener anywhere yet on this branch — the RabbitMQ dependency is wired,
// same "add it unused first" pattern sessions 4 and 5 used for their verification
// tools. Consuming BookingCreatedMessage/BookingCancelledMessage is this session's
// lab, not baseline.
@SpringBootApplication
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

}
