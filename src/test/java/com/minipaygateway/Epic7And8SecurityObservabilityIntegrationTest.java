package com.minipaygateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.domain.enums.AccountType;
import com.minipaygateway.dto.request.CreateAccountRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
		"app.rate-limit.requests-per-minute=2"
})
class Epic7And8SecurityObservabilityIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
			.withDatabaseName("paygateway")
			.withUsername("pguser")
			.withPassword("pgsecret");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void paymentEndpoints_rateLimit_returns429WithProblemDetail() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("200.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String body = objectMapper
				.writeValueAsString(new InitiatePayload(payer, payee, new BigDecimal("1.00"), "USD"));

		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
	}

	@Test
	void traceId_isGeneratedOrEchoedOnResponse() throws Exception {
		String admin = fetchToken("admin", "admin");
		mockMvc.perform(get("/api/v1/payments").header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(header().exists("X-Trace-Id"));

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + admin)
				.header("X-Trace-Id", "trace-abc"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Trace-Id", "trace-abc"));
	}

	private long createAccount(String adminToken, String ownerRef, String currency, BigDecimal initialBalance)
			throws Exception {
		var body = new CreateAccountRequest(ownerRef, currency, AccountType.MERCHANT, initialBalance);
		String json = mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode node = objectMapper.readTree(json);
		return node.get("id").asLong();
	}

	private String fetchToken(String user, String pass) throws Exception {
		String json = mockMvc.perform(post("/api/v1/auth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("token").asText();
	}

	private record InitiatePayload(long payerAccountId, long payeeAccountId, BigDecimal amount, String currency) {
	}
}
