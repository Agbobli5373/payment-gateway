package com.minipaygateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
class Epic10ContractsIntegrationTest {

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
	void initiate_sameIdempotencyKeyDifferentBody_returns409Conflict() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("200.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		UUID key = UUID.randomUUID();

		String body1 = objectMapper.writeValueAsString(new InitiatePayload(payer, payee, new BigDecimal("5.00"), "USD"));
		String body2 = objectMapper.writeValueAsString(new InitiatePayload(payer, payee, new BigDecimal("6.00"), "USD"));

		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body1))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body2))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
	}

	@Test
	void merchant_settleEndpoint_returns403() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("100.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("4.00"), "USD");

		mockMvc.perform(post("/api/v1/payments/{ref}/settle", ref)
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	private String initiatePayment(String merchantToken, long payer, long payee, BigDecimal amount, String currency)
			throws Exception {
		String body = objectMapper.writeValueAsString(new InitiatePayload(payer, payee, amount, currency));
		String response = mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchantToken)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).get("reference").asText();
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
