package com.minipaygateway.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.domain.IdempotencyKey;
import com.minipaygateway.exception.IdempotencyKeyConflictException;
import com.minipaygateway.repository.IdempotencyKeyRepository;

import jakarta.persistence.EntityManager;

@Service
public class IdempotencyService {

	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final EntityManager entityManager;
	private final ObjectMapper objectMapper;
	private final Duration ttl;

	public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository, EntityManager entityManager,
			ObjectMapper objectMapper, @Value("${app.idempotency.ttl:PT24H}") String ttl) {
		this.idempotencyKeyRepository = idempotencyKeyRepository;
		this.entityManager = entityManager;
		this.objectMapper = objectMapper;
		this.ttl = Duration.parse(ttl);
	}

	public static String sha256Hex(String body) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Transactional
	public IdempotencyExecutionResult execute(UUID key, String requestHash, Supplier<IdempotentResult> operation) {
		acquireTransactionAdvisoryLock(key);
		Optional<IdempotencyKey> existing = idempotencyKeyRepository.findById(key);
		if (existing.isPresent()) {
			IdempotencyKey row = existing.get();
			if (!row.getRequestHash().equals(requestHash)) {
				throw new IdempotencyKeyConflictException();
			}
			if (row.getExpiresAt().isBefore(Instant.now())) {
				idempotencyKeyRepository.delete(row);
				entityManager.flush();
			}
			else {
				return new IdempotencyExecutionResult(row.getHttpStatus(), row.getResponseBody());
			}
		}

		IdempotentResult result = operation.get();
		try {
			String json = objectMapper.writeValueAsString(result.body());
			IdempotencyKey row = new IdempotencyKey();
			row.setKey(key);
			row.setRequestHash(requestHash);
			row.setHttpStatus((short) result.httpStatus());
			row.setResponseBody(json);
			row.setExpiresAt(Instant.now().plus(ttl));
			idempotencyKeyRepository.save(row);
			return new IdempotencyExecutionResult(result.httpStatus(), json);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private void acquireTransactionAdvisoryLock(UUID uuid) {
		long msb = uuid.getMostSignificantBits();
		long lsb = uuid.getLeastSignificantBits();
		int k1 = (int) (msb >> 32 ^ lsb >> 32);
		int k2 = (int) (msb ^ lsb);
		entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:a, :b)")
				.setParameter("a", k1)
				.setParameter("b", k2)
				.getSingleResult();
	}

	public record IdempotentResult(int httpStatus, Object body) {
	}

	public record IdempotencyExecutionResult(int httpStatus, String responseBodyJson) {
	}
}
