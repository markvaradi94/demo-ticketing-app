package io.callisto.paymentservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// APPROVE_SLOWLY genuinely sleeps 5s — this is what gives core-app's Resilience4j
// TimeLimiter something real to time out against, and what the virtual-threads demo
// blocks on under concurrent load. Not something to unit-test the timing of; the
// classification itself (PaymentOutcome.forAmount) is what's actually tested.
@RestController
@RequestMapping("/payments")
class PaymentController {

	@PostMapping
	ResponseEntity<PaymentResponse> charge(@RequestBody PaymentRequest request) {
		return switch (PaymentOutcome.forAmount(request.amount())) {
			case DECLINE -> ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(new PaymentResponse("DECLINED"));
			case APPROVE_SLOWLY -> {
				sleep();
				yield ResponseEntity.ok(new PaymentResponse("APPROVED"));
			}
			case APPROVE -> ResponseEntity.ok(new PaymentResponse("APPROVED"));
		};
	}

	private static void sleep() {
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
