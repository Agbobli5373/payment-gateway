package com.minipaygateway.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.minipaygateway.domain.enums.ReconciliationRunStatus;

public record ReconciliationReportSummaryResponse(long id, LocalDate periodStart, LocalDate periodEnd,
		Instant reconcileFrom, Instant reconcileTo, String currency, int discrepancyCount,
		ReconciliationRunStatus status, int expectedCount, BigDecimal expectedSum, int actualCount,
		BigDecimal actualSum, Instant completedAt, Instant createdAt) {
}
