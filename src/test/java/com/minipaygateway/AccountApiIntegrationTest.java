package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.domain.IdempotencyKey;
import com.minipaygateway.domain.enums.AccountType;
import com.minipaygateway.dto.request.CreateAccountRequest;
import com.minipaygateway.repository.IdempotencyKeyRepository;
import com.minipaygateway.scheduler.IdempotencyCleanupJob;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountApiIntegrationTest {

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
	IdempotencyKeyRepository idempotencyKeyRepository;

	@Autowired
	IdempotencyCleanupJob idempotencyCleanupJob;

	@Test
	void adminCreatesAccount_merchantCanReadBalance_merchantCannotCreate() throws Exception {
		String adminToken = fetchToken("admin", "admin");
		String merchantToken = fetchToken("merchant", "merchant");

		var body = new CreateAccountRequest("merchant-acme", "USD", AccountType.MERCHANT, new BigDecimal("100.00"));
		String createJson = objectMapper.writeValueAsString(body);

		mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + merchantToken)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson))
				.andExpect(status().isForbidden());

		String created = mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerRef").value("merchant-acme"))
				.andExpect(jsonPath("$.currency").value("USD"))
				.andReturn().getResponse().getContentAsString();

		long id = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/v1/accounts/{id}/balance", id)
				.header("Authorization", "Bearer " + merchantToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(id))
				.andExpect(jsonPath("$.currency").value("USD"))
				.andExpect(jsonPath("$.balance").value("100"));
	}

	@Test
	void postAccountWithoutIdempotencyKey_returns400() throws Exception {
		String adminToken = fetchToken("admin", "admin");
		var body = new CreateAccountRequest("x", "USD", AccountType.MERCHANT, BigDecimal.ZERO);
		mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
	}

	@Test
	void badCredentials_returns401() throws Exception {
		mockMvc.perform(post("/api/v1/auth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getBalance_unknownAccount_returns404() throws Exception {
		String adminToken = fetchToken("admin", "admin");
		mockMvc.perform(get("/api/v1/accounts/{id}/balance", 9_999_999_999L)
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
	}

	@Test
	void merchantCannotReadOtherOwnerBalance_returns403() throws Exception {
		String adminToken = fetchToken("admin", "admin");
		String merchantOtherToken = fetchToken("merchant-other", "merchant");

		var body = new CreateAccountRequest("sole-proprietor-x", "USD", AccountType.MERCHANT, new BigDecimal("10.00"));
		String createJson = objectMapper.writeValueAsString(body);
		String created = mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createJson))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long id = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/v1/accounts/{id}/balance", id)
				.header("Authorization", "Bearer " + merchantOtherToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void createAccount_sameIdempotencyKeyAndBody_replaysSameResponse() throws Exception {
		String adminToken = fetchToken("admin", "admin");
		UUID key = UUID.randomUUID();
		var body = new CreateAccountRequest("idempo-a", "USD", AccountType.MERCHANT, BigDecimal.ZERO);
		String json = objectMapper.writeValueAsString(body);

		String first = mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		String second = mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		long id1 = objectMapper.readTree(first).get("id").asLong();
		long id2 = objectMapper.readTree(second).get("id").asLong();
		assertThat(id2).isEqualTo(id1);
	}

	@Test
	void createAccount_sameIdempotencyKeyDifferentBody_returns409() throws Exception {
		String adminToken = fetchToken("admin", "admin");
		UUID key = UUID.randomUUID();
		var body1 = new CreateAccountRequest("idempo-b", "USD", AccountType.MERCHANT, BigDecimal.ZERO);
		var body2 = new CreateAccountRequest("idempo-c", "USD", AccountType.MERCHANT, BigDecimal.ZERO);

		mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body1)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body2)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
	}

	@Test
	void createAccount_afterExpiredKeyPurged_sameIdempotencyUuidCanCreateAgain() throws Exception {
		UUID key = UUID.randomUUID();
		IdempotencyKey row = new IdempotencyKey();
		row.setKey(key);
		row.setRequestHash("stale");
		row.setHttpStatus((short) 201);
		row.setResponseBody("{\"id\":0}");
		row.setExpiresAt(Instant.now().minusSeconds(7_200));
		idempotencyKeyRepository.save(row);

		idempotencyCleanupJob.purgeExpiredIdempotencyKeys();

		String adminToken = fetchToken("admin", "admin");
		var body = new CreateAccountRequest("post-purge-owner", "USD", AccountType.MERCHANT, BigDecimal.ZERO);
		mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + adminToken)
				.header("X-Idempotency-Key", key.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerRef").value("post-purge-owner"));
	}

	private String fetchToken(String user, String pass) throws Exception {
		String json = mockMvc.perform(post("/api/v1/auth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("token").asText();
	}
}
