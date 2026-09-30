package io.callisto.ticketing.booking.adapter.out.payment;

import io.callisto.ticketing.booking.PaymentDeclinedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit test: {@link PaymentAdapter}'s HTTP-response translation only — a 402 becomes
 * {@link PaymentDeclinedException}, success returns normally — using
 * {@link MockRestServiceServer} against a plain {@link RestClient}, no Spring
 * context. Deliberately doesn't exercise @Retry/@CircuitBreaker: those only activate
 * through Spring's AOP proxy on a container-managed bean, not on a directly
 * constructed instance, and are framework behavior this course trusts rather than
 * re-tests — they're demonstrated live against a real running payment-service
 * instead, including the genuine 5s slow path this test never pays for.
 */
class PaymentAdapterTest {

	private final RestClient.Builder builder = RestClient.builder().baseUrl("http://payment-service");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final PaymentAdapter adapter = new PaymentAdapter(builder.build());

	@Test
	void succeedsWhenPaymentServiceApproves() {
		server.expect(requestTo("http://payment-service/payments"))
				.andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

		assertThatCode(() -> adapter.charge("ref-1", new BigDecimal("50.00"))).doesNotThrowAnyException();
	}

	@Test
	void translatesADeclineIntoPaymentDeclinedException() {
		server.expect(requestTo("http://payment-service/payments"))
				.andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"status\":\"DECLINED\"}"));

		assertThatThrownBy(() -> adapter.charge("ref-2", new BigDecimal("2500.00")))
				.isInstanceOf(PaymentDeclinedException.class);
	}

}
