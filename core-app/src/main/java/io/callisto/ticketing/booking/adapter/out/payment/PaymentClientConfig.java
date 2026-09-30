package io.callisto.ticketing.booking.adapter.out.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// The connect/read timeout configured here IS this adapter's timeout protection —
// deliberately not @TimeLimiter. @TimeLimiter only does anything useful on a method
// returning CompletableFuture (it races the future against a timer); it can't act on
// a plain blocking RestClient call the way this codebase has stayed synchronous
// throughout. A client-side read timeout is the honest equivalent for blocking I/O,
// and what actually gives PaymentAdapter's @Retry/@CircuitBreaker something to react
// to when payment-service's slow path runs past it.
@Configuration
@EnableConfigurationProperties(PaymentServiceProperties.class)
class PaymentClientConfig {

	@Bean
	RestClient paymentRestClient(RestClient.Builder builder, PaymentServiceProperties properties) {
		HttpClientSettings settings = HttpClientSettings.defaults()
				.withConnectTimeout(properties.connectTimeout())
				.withReadTimeout(properties.readTimeout());
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
		return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

}
