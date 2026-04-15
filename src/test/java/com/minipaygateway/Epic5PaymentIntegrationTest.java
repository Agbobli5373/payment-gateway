package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.minipaygateway.repository.LedgerEntryRepository;
import com.minipaygateway.repository.PaymentTransactionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class Epic5PaymentIntegrationTest {

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
	LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	PaymentTransactionRepository paymentTransactionRepository;

	@Test
	void initiate_process_settle_reverse_happyPath_ledgerAndBalances() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("200.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);

		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("25.0000"), "USD");
		assertThat(ledgerEntryRepository.findByPaymentTransactionIdOrderByIdAsc(requirePaymentId(ref))).isEmpty();

		postMutation(admin, "/api/v1/payments/{ref}/process", ref, "{}");
		assertThat(ledgerEntryRepository.findByPaymentTransactionIdOrderByIdAsc(
				requirePaymentId(ref))).hasSize(1);

		postMutation(admin, "/api/v1/payments/{ref}/settle", ref, "{}");
		assertThat(ledgerEntryRepository.findByPaymentTransactionIdOrderByIdAsc(
				requirePaymentId(ref))).hasSize(2);

		postMutation(admin, "/api/v1/payments/{ref}/reverse", ref, "{}");
		assertThat(ledgerEntryRepository.findByPaymentTransactionIdOrderByIdAsc(
				requirePaymentId(ref))).hasSize(3);

		mockMvc.perform(get("/api/v1/payments/{ref}", ref).header("Authorization", "Bearer " + merchant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVERSED"))
				.andExpect(jsonPath("$.ledgerEntryIds.length()").value(3));
	}

	@Test
	void initiate_insufficientBalance_returns422() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("5.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);

		String body = objectMapper.writeValueAsString(new InitiatePayload(payer, payee, new BigDecimal("10.00"), "USD"));
		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
	}

	@Test
	void settleFromPending_returnsInvalidTransition() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("50.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("5.00"), "USD");

		mockMvc.perform(post("/api/v1/payments/{ref}/settle", ref)
				.header("Authorization", "Bearer " + admin)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
	}

	@Test
	void failPending_noLedgerRows() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("50.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("5.00"), "USD");
		long pid = requirePaymentId(ref);

		postMutation(admin, "/api/v1/payments/{ref}/fail", ref, "{}");

		assertThat(ledgerEntryRepository.findByPaymentTransactionIdOrderByIdAsc(pid)).isEmpty();
		mockMvc.perform(get("/api/v1/payments/{ref}", ref).header("Authorization", "Bearer " + merchant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));
	}

	@Test
	void initiate_sameIdempotencyKey_replaysSameReference() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("100.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		UUID key = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(new InitiatePayload(payer, payee, new BigDecimal("3.00"), "USD"));

		String first = mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String second = mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + merchant)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		String ref1 = objectMapper.readTree(first).get("reference").asText();
		String ref2 = objectMapper.readTree(second).get("reference").asText();
		assertThat(ref2).isEqualTo(ref1);
	}

	@Test
	void list_filtersByStatus_andMerchantScope() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchantAcme = fetchToken("merchant", "merchant");
		String merchantOther = fetchToken("merchant-other", "merchant");

		long payerAcme = createAccount(admin, "merchant-acme", "USD", new BigDecimal("500.00"));
		long payeeOther = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String pendingRef = initiatePayment(merchantAcme, payerAcme, payeeOther, new BigDecimal("1.00"), "USD");

		long payerOtherEur = createAccount(admin, "other-owner", "EUR", new BigDecimal("500.00"));
		long payeeAcmeEur = createAccount(admin, "merchant-acme", "EUR", BigDecimal.ZERO);
		initiatePayment(merchantOther, payerOtherEur, payeeAcmeEur, new BigDecimal("2.00"), "EUR");

		mockMvc.perform(get("/api/v1/payments").param("status", "PENDING")
				.header("Authorization", "Bearer " + merchantAcme))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].reference").value(pendingRef));

		mockMvc.perform(get("/api/v1/payments").header("Authorization", "Bearer " + merchantAcme))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1));

		mockMvc.perform(get("/api/v1/payments").header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2));
	}

	@Test
	void initiate_malformedJson_returns400() throws Exception {
		String admin = fetchToken("admin", "admin");
		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + admin)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("not-json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void process_sameIdempotencyKeyAndBody_replaysSameResponse() throws Exception {
		String admin = fetchToken("admin", "admin");
		String merchant = fetchToken("merchant", "merchant");
		long payer = createAccount(admin, "merchant-acme", "USD", new BigDecimal("100.00"));
		long payee = createAccount(admin, "other-owner", "USD", BigDecimal.ZERO);
		String ref = initiatePayment(merchant, payer, payee, new BigDecimal("10.00"), "USD");
		UUID key = UUID.randomUUID();
		String first = mockMvc.perform(post("/api/v1/payments/{ref}/process", ref)
				.header("Authorization", "Bearer " + admin)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String second = mockMvc.perform(post("/api/v1/payments/{ref}/process", ref)
				.header("Authorization", "Bearer " + admin)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(second).isEqualTo(first);
	}

	private long requirePaymentId(String reference) {
		return paymentTransactionRepository.findByReference(reference).orElseThrow().getId();
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
