package com.minipaygateway;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.dto.request.CreateAccountRequest;
import com.minipaygateway.domain.enums.AccountType;

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

	private String fetchToken(String user, String pass) throws Exception {
		String json = mockMvc.perform(post("/api/v1/auth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("token").asText();
	}
}
