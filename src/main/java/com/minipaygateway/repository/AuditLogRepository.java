package com.minipaygateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.minipaygateway.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
