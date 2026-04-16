package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.domain.enums.AccountType;
import com.minipaygateway.dto.request.CreateAccountRequest;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class PaymentLifecycleEndToEndFlowIntegrationTest {

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

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	EntityManager entityManager;

	@Test
	void reverse_thenReconcile_returnsNoDiscrepancies() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");

		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("200.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);

		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("25.0000"), "USD");

		postMutation(admin, "/api/v1/payments/{ref}/process", ref, "{}");
		postMutation(admin, "/api/v1/payments/{ref}/settle", ref, "{}");
		postMutation(admin, "/api/v1/payments/{ref}/reverse", ref, "{}");

		mockMvc.perform(get("/api/v1/payments/{ref}", ref).header("Authorization", "Bearer " + merchant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVERSED"))
				.andExpect(jsonPath("$.ledgerEntryIds.length()").value(3));

		String response = postReconcile(admin, newWindowAroundNow("USD"));
		JsonNode node = objectMapper.readTree(response);
		assertThat(node.get("discrepancyCount").asInt()).isEqualTo(0);
		long reportId = node.get("id").asLong();
		mockMvc.perform(get("/api/v1/reconcile/reports/{id}", reportId).header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancies.length()").value(0));
	}

	@Test
	void settle_thenReconcile_returnsNoDiscrepancies() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");

		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("500.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);

		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("12.34"), "USD");

		postMutation(admin, "/api/v1/payments/{ref}/process", ref, "{}");
		postMutation(admin, "/api/v1/payments/{ref}/settle", ref, "{}");

		String response = postReconcile(admin, newWindowAroundNow("USD"));
		JsonNode node = objectMapper.readTree(response);
		assertThat(node.get("discrepancyCount").asInt()).isEqualTo(0);
		long reportId = node.get("id").asLong();
		mockMvc.perform(get("/api/v1/reconcile/reports/{id}", reportId).header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancies.length()").value(0));
	}

	@Test
	void injectedAmountMismatch_thenReconcile_detectsMismatch() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");

		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("500.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);

		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("10.00"), "USD");

		postMutation(admin, "/api/v1/payments/{ref}/process", ref, "{}");
		postMutation(admin, "/api/v1/payments/{ref}/settle", ref, "{}");

		long paymentId = jdbcTemplate.queryForObject(
				"select id from payment_transactions where reference = ?", Long.class, ref);

		// Inject a mismatch in settlement ledger entries so reconciliation can classify it.
		jdbcTemplate.update(
				"update ledger_entries set amount = amount + 1 where transaction_id = ? and reference = 'SETTLE'",
				paymentId);
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(newWindowAroundNow("USD")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancyCount").value(1))
				.andExpect(jsonPath("$.discrepancies[0].type").value("AMOUNT_MISMATCH"));
	}

	private String postReconcile(String adminToken, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andReturn().getResponse().getContentAsString();
	}

	private String newWindowAroundNow(String currency) {
		Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
		return "{\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"currency\":\"" + currency + "\"}";
	}

	private void postMutation(String adminToken, String uriTemplate, String ref, String json) throws Exception {
		mockMvc.perform(post(uriTemplate, ref)
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
				.andExpect(status().isOk());
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
				.andExpect(jsonPath("$.status").value("PENDING"))
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

