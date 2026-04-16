package com.minipaygateway.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.minipaygateway.domain.enums.ReconciliationRunStatus;

public record ReconciliationReportDetailResponse(long id, LocalDate periodStart, LocalDate periodEnd,
		Instant reconcileFrom, Instant reconcileTo, String currency, int discrepancyCount,
		ReconciliationRunStatus status, int expectedCount, BigDecimal expectedSum, int actualCount,
		BigDecimal actualSum, Instant completedAt, Instant createdAt,
		List<ReconciliationDiscrepancyResponse> discrepancies) {

	public ReconciliationReportDetailResponse {
		discrepancies = discrepancies == null ? List.of() : List.copyOf(discrepancies);
	}
}
