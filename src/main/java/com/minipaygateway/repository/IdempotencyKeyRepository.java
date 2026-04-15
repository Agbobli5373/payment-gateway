package com.minipaygateway.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.minipaygateway.domain.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
}
