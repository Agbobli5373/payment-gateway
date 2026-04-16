package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
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

import jakarta.persistence.EntityManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.domain.enums.AccountType;
import com.minipaygateway.domain.enums.PaymentStatus;
import com.minipaygateway.dto.request.CreateAccountRequest;
import com.minipaygateway.repository.ReconciliationReportRepository;
import com.minipaygateway.scheduler.DailyReconcileJob;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class Epic6ReconciliationIntegrationTest {

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
	ReconciliationReportRepository reconciliationReportRepository;

	@Autowired
	DailyReconcileJob dailyReconcileJob;

	@Autowired
	EntityManager entityManager;

	@Test
	void merchant_postReconcile_returns403() throws Exception {
		String token = fetchToken("merchant", "merchant");
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"{\"from\":\"2020-01-01T00:00:00Z\",\"to\":\"2020-01-02T00:00:00Z\",\"currency\":\"USD\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void reconcile_settledPayment_happyPath_noDiscrepancies() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("500.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("12.34"), "USD");
		postMutation(admin, "/api/v1/payments/{ref}/process", ref, "{}");
		postMutation(admin, "/api/v1/payments/{ref}/settle", ref, "{}");

		Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
		String reconcileJson = "{\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"currency\":\"USD\"}";

		String response = 		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reconcileJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancyCount").value(0))
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.reconcileFrom").exists())
				.andExpect(jsonPath("$.reconcileTo").exists())
				.andReturn().getResponse().getContentAsString();
		long reportId = objectMapper.readTree(response).get("id").asLong();

		mockMvc.perform(get("/api/v1/reconcile/reports/{id}", reportId).header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancies.length()").value(0));

		mockMvc.perform(get("/api/v1/reconcile/reports").header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void reconcile_missingLedgerEntry() throws Exception {
		String admin = fetchToken("admin", "admin");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("100.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		Instant settledAt = Instant.parse("2021-06-15T12:00:00Z");
		var settledTs = Timestamp.from(settledAt);
		jdbcTemplate.update("""
				INSERT INTO payment_transactions (reference, payer_account_id, payee_account_id, amount, currency, status, settled_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", "orph-missing-" + UUID.randomUUID(), payer, payee, new BigDecimal("5.0000"), "USD",
				PaymentStatus.SETTLED.name(), settledTs, settledTs, settledTs);

		String reconcileJson = "{\"from\":\"2021-06-15T00:00:00Z\",\"to\":\"2021-06-15T23:59:59Z\",\"currency\":\"USD\"}";
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reconcileJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancyCount").value(1))
				.andExpect(jsonPath("$.status").value("COMPLETED_WITH_DISCREPANCIES"))
				.andExpect(jsonPath("$.discrepancies[0].type").value("MISSING_LEDGER_ENTRY"));
	}

	@Test
	void reconcile_amountMismatch() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("500.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("10.00"), "USD");
		postMutation(admin, "/api/v1/payments/{ref}/process", ref, "{}");
		postMutation(admin, "/api/v1/payments/{ref}/settle", ref, "{}");

		Long paymentId = jdbcTemplate.queryForObject(
				"select id from payment_transactions where reference = ?", Long.class, ref);
		jdbcTemplate.update(
				"update ledger_entries set amount = amount + 1 where transaction_id = ? and reference = 'SETTLE'",
				paymentId);
		entityManager.flush();
		entityManager.clear();

		Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
		String reconcileJson = "{\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"currency\":\"USD\"}";
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reconcileJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancyCount").value(1))
				.andExpect(jsonPath("$.discrepancies[0].type").value("AMOUNT_MISMATCH"));
	}

	@Test
	void reconcile_orphanedLedgerEntry() throws Exception {
		String admin = fetchToken("admin", "admin");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("100.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		Instant ts = Instant.parse("2022-03-10T10:00:00Z");
		jdbcTemplate.update("""
				INSERT INTO ledger_entries (debit_account_id, credit_account_id, amount, currency, transaction_id, reference, created_at)
				VALUES (?, ?, ?, ?, NULL, 'SETTLE', ?)
				""", payer, payee, new BigDecimal("1.0000"), "USD", Timestamp.from(ts));

		String reconcileJson = "{\"from\":\"2022-03-10T00:00:00Z\",\"to\":\"2022-03-10T23:59:59Z\",\"currency\":\"USD\"}";
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reconcileJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancyCount").value(1))
				.andExpect(jsonPath("$.discrepancies[0].type").value("ORPHANED_LEDGER_ENTRY"));
	}

	@Test
	void reconcile_afterReverse_doesNotReportOrphansForLifecycleLedger() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("500.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("7.00"), "USD");
		postMutation(admin, "/api/v1/payments/{ref}/process", ref, "{}");
		postMutation(admin, "/api/v1/payments/{ref}/settle", ref, "{}");
		postMutation(admin, "/api/v1/payments/{ref}/reverse", ref, "{}");

		Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
		String reconcileJson = "{\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"currency\":\"USD\"}";
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reconcileJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discrepancyCount").value(0))
				.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@Test
	void getReport_corruptDiscrepancyJson_returns500() throws Exception {
		String admin = fetchToken("admin", "admin");
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"from\":\"2019-01-01T00:00:00Z\",\"to\":\"2019-01-02T00:00:00Z\",\"currency\":\"USD\"}"))
				.andExpect(status().isOk());
		long id = jdbcTemplate.queryForObject("select max(id) from reconciliation_reports", Long.class);
		jdbcTemplate.update("update reconciliation_reports set discrepancy_details = ?::jsonb where id = ?",
				"{\"unexpected\": \"object\"}", id);
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(get("/api/v1/reconcile/reports/{id}", id).header("Authorization", "Bearer " + admin))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("RECONCILIATION_REPORT_CORRUPT"));
	}

	@Test
	void dailyReconcileJob_runsWithoutError() {
		long before = reconciliationReportRepository.count();
		dailyReconcileJob.reconcilePriorUtcDay();
		assertThat(reconciliationReportRepository.count()).isGreaterThanOrEqualTo(before);
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
