package io.callisto.ticketing.catalog;

import io.callisto.ticketing.catalog.dto.EventRequest;
import io.callisto.ticketing.catalog.dto.EventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EventControllerTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void createsAndFetchesAnEvent() {
		EventRequest request = new EventRequest("Jazz Night", "Blue Room", 120, Instant.now().plus(30, ChronoUnit.DAYS));

		ResponseEntity<EventResponse> created = rest.postForEntity("/events", request, EventResponse.class);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getHeaders().getLocation()).isNotNull();
		String id = created.getBody().id();

		ResponseEntity<EventResponse> fetched = rest.getForEntity("/events/" + id, EventResponse.class);

		assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(fetched.getBody().name()).isEqualTo("Jazz Night");
		assertThat(fetched.getBody().venueCapacity()).isEqualTo(120);
	}

	@Test
	void rejectsAnInvalidEvent() {
		EventRequest blankName = new EventRequest(" ", "Blue Room", 120, Instant.now().plus(30, ChronoUnit.DAYS));

		ResponseEntity<String> response = rest.postForEntity("/events", blankName, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void returnsNotFoundForAnUnknownEvent() {
		ResponseEntity<String> response = rest.getForEntity("/events/does-not-exist", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deletesAnEvent() {
		EventRequest request = new EventRequest("Comedy Set", "Attic", 60, Instant.now().plus(10, ChronoUnit.DAYS));
		String id = rest.postForEntity("/events", request, EventResponse.class).getBody().id();

		ResponseEntity<Void> deleted = rest.exchange("/events/" + id, HttpMethod.DELETE, null, Void.class);

		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(rest.getForEntity("/events/" + id, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
