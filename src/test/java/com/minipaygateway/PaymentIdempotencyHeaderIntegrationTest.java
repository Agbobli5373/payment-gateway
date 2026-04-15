package com.minipaygateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(PaymentIdempotencyHeaderIntegrationTest.PaymentStubConfig.class)
class PaymentIdempotencyHeaderIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
			.withDatabaseName("paygateway")
			.withUsername("pguser")
			.withPassword("pgsecret");

	@Autowired
	MockMvc mockMvc;

	@Test
	void postPaymentPath_withoutIdempotencyKey_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/payments/stub")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
	}

	@TestConfiguration
	static class PaymentStubConfig {

		@Bean
		PaymentStubController paymentStubController() {
			return new PaymentStubController();
		}
	}

	@RestController
	static class PaymentStubController {

		@PostMapping("/api/v1/payments/stub")
		ResponseEntity<Void> stub() {
			return ResponseEntity.noContent().build();
		}
	}
}
