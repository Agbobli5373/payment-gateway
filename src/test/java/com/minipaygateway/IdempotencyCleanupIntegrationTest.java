package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.minipaygateway.domain.IdempotencyKey;
import com.minipaygateway.repository.IdempotencyKeyRepository;
import com.minipaygateway.scheduler.IdempotencyCleanupJob;

@SpringBootTest
@Testcontainers
class IdempotencyCleanupIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
			.withDatabaseName("paygateway")
			.withUsername("pguser")
			.withPassword("pgsecret");

	@Autowired
	IdempotencyKeyRepository idempotencyKeyRepository;

	@Autowired
	IdempotencyCleanupJob idempotencyCleanupJob;

	@Test
	void purgeExpiredIdempotencyKeys_removesOnlyExpiredRows() {
		Instant now = Instant.now();
		UUID expiredId = UUID.randomUUID();
		UUID activeId = UUID.randomUUID();

		IdempotencyKey expired = new IdempotencyKey();
		expired.setKey(expiredId);
		expired.setRequestHash("deadbeef");
		expired.setHttpStatus((short) 201);
		expired.setResponseBody("{}");
		expired.setExpiresAt(now.minusSeconds(3_600));
		idempotencyKeyRepository.save(expired);

		IdempotencyKey active = new IdempotencyKey();
		active.setKey(activeId);
		active.setRequestHash("cafebabe");
		active.setHttpStatus((short) 201);
		active.setResponseBody("{}");
		active.setExpiresAt(now.plusSeconds(3_600));
		idempotencyKeyRepository.save(active);

		idempotencyCleanupJob.purgeExpiredIdempotencyKeys();

		assertThat(idempotencyKeyRepository.findById(expiredId)).isEmpty();
		assertThat(idempotencyKeyRepository.findById(activeId)).isPresent();
	}
}
