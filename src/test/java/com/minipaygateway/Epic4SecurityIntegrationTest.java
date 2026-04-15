package com.minipaygateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Epic4SecurityIntegrationTest {

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

	@Value("${app.jwt.secret}")
	String jwtSecret;

	@Test
	void protectedEndpoint_withoutBearer_returns401() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/{id}/balance", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void protectedEndpoint_withMalformedJwt_returns401() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/{id}/balance", 1L)
				.header("Authorization", "Bearer not-a-valid-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void protectedEndpoint_withExpiredJwt_returns401() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/{id}/balance", 1L)
				.header("Authorization", "Bearer " + buildExpiredJwt()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void merchant_postAccounts_returns403() throws Exception {
		String token = fetchToken("merchant", "merchant");
		mockMvc.perform(post("/api/v1/accounts")
				.header("Authorization", "Bearer " + token)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ownerRef\":\"x\",\"currency\":\"USD\",\"accountType\":\"MERCHANT\",\"initialBalance\":0}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void merchant_postPaymentProcess_returns403() throws Exception {
		String token = fetchToken("merchant", "merchant");
		mockMvc.perform(post("/api/v1/payments/1/process")
				.header("Authorization", "Bearer " + token)
				.header("X-Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void merchant_postPaymentInitiate_returns501() throws Exception {
		String token = fetchToken("merchant", "merchant");
		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + token)
				.header("X-Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isNotImplemented())
				.andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
	}

	@Test
	void admin_postPaymentInitiate_returns501() throws Exception {
		String token = fetchToken("admin", "admin");
		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + token)
				.header("X-Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isNotImplemented());
	}

	@Test
	void auditor_postPaymentInitiate_returns403() throws Exception {
		String token = fetchToken("auditor", "auditor");
		mockMvc.perform(post("/api/v1/payments/initiate")
				.header("Authorization", "Bearer " + token)
				.header("X-Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isForbidden());
	}

	@Test
	void merchant_postReconcile_returns403() throws Exception {
		String token = fetchToken("merchant", "merchant");
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void auditor_postReconcile_returns501() throws Exception {
		String token = fetchToken("auditor", "auditor");
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotImplemented());
	}

	@Test
	void admin_postReconcile_returns501() throws Exception {
		String token = fetchToken("admin", "admin");
		mockMvc.perform(post("/api/v1/reconcile")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotImplemented());
	}

	@Test
	void auditor_getPayment_returns403() throws Exception {
		String token = fetchToken("auditor", "auditor");
		mockMvc.perform(get("/api/v1/payments/{id}", 1L)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void merchant_getPayment_returns501() throws Exception {
		String token = fetchToken("merchant", "merchant");
		mockMvc.perform(get("/api/v1/payments/{id}", 1L)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotImplemented());
	}

	@Test
	void auditor_getReconcileReport_returns501() throws Exception {
		String token = fetchToken("auditor", "auditor");
		mockMvc.perform(get("/api/v1/reconcile/reports/{id}", 1L)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotImplemented());
	}

	private String fetchToken(String user, String pass) throws Exception {
		String json = mockMvc.perform(post("/api/v1/auth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("token").asText();
	}

	private String buildExpiredJwt() throws Exception {
		byte[] material = jwtSecret.getBytes(StandardCharsets.UTF_8);
		if (material.length < 32) {
			material = MessageDigest.getInstance("SHA-256").digest(material);
		}
		SecretKey key = Keys.hmacShaKeyFor(material);
		var now = System.currentTimeMillis();
		return Jwts.builder()
				.subject("admin")
				.claim("roles", List.of("ADMIN"))
				.issuedAt(new Date(now - 120_000))
				.expiration(new Date(now - 60_000))
				.signWith(key)
				.compact();
	}
}
