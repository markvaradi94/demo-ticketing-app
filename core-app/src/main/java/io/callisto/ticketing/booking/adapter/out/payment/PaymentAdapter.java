package io.callisto.ticketing.booking.adapter.out.payment;

import io.callisto.ticketing.booking.PaymentDeclinedException;
import io.callisto.ticketing.booking.application.port.out.PaymentPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

// Package-private, same reasoning as every other adapter this course has built —
// only PaymentPort needs to be public.
//
// @Retry wraps @CircuitBreaker wraps this method (Resilience4j's aspect order is
// fixed, not determined by annotation order on the method). Both are configured in
// application.properties to ignore PaymentDeclinedException — a decline is
// payment-service correctly doing its job, not a fault, and shouldn't count against
// either pattern the way a timeout or a 5xx does.
@Component
@RequiredArgsConstructor
class PaymentAdapter implements PaymentPort {

	private final RestClient paymentRestClient;

	@Override
	@Retry(name = "payment")
	@CircuitBreaker(name = "payment")
	public void charge(String reference, BigDecimal amount) {
		try {
			paymentRestClient.post()
					.uri("/payments")
					.body(new PaymentChargeRequest(reference, amount))
					.retrieve()
					.body(PaymentChargeResponse.class);
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
				throw new PaymentDeclinedException(reference);
			}
			throw e;
		}
	}

}
