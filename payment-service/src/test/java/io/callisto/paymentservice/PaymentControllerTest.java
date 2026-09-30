package io.callisto.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer test only — the APPROVE and DECLINE paths, both instant. The
 * APPROVE_SLOWLY path genuinely sleeps 5s in real usage (that's the point, for
 * core-app's timeout demo) and is deliberately not exercised here; it's verified via
 * {@link PaymentOutcomeTest}'s classification test instead, without paying the wait.
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void approvesASmallPayment() throws Exception {
		PaymentRequest request = new PaymentRequest("ref-1", new BigDecimal("50.00"));

		mockMvc.perform(post("/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void declinesALargePayment() throws Exception {
		PaymentRequest request = new PaymentRequest("ref-2", new BigDecimal("2500.00"));

		mockMvc.perform(post("/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(request)))
				.andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.status").value("DECLINED"));
	}

}
