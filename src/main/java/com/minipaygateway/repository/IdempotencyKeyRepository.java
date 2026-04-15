package com.minipaygateway.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.minipaygateway.domain.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :now")
	int deleteExpiredBefore(@Param("now") Instant now);
}
