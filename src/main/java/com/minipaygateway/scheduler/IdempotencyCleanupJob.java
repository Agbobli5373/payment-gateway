package com.minipaygateway.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.minipaygateway.repository.IdempotencyKeyRepository;

@Component
public class IdempotencyCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

	private final IdempotencyKeyRepository idempotencyKeyRepository;

	public IdempotencyCleanupJob(IdempotencyKeyRepository idempotencyKeyRepository) {
		this.idempotencyKeyRepository = idempotencyKeyRepository;
	}

	@Scheduled(cron = "${app.idempotency.cleanup-cron}")
	public void purgeExpiredIdempotencyKeys() {
		int removed = idempotencyKeyRepository.deleteExpiredBefore(Instant.now());
		if (log.isDebugEnabled() && removed > 0) {
			log.debug("Purged {} expired idempotency keys", removed);
		}
	}
}
