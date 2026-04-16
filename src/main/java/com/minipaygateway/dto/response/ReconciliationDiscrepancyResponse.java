package com.minipaygateway.dto.response;

import java.math.BigDecimal;

import com.minipaygateway.domain.enums.DiscrepancyType;

public record ReconciliationDiscrepancyResponse(DiscrepancyType type, Long paymentTransactionId,
		String paymentReference, Long ledgerEntryId, String ledgerReference, BigDecimal expectedAmount,
		BigDecimal actualAmount, String detail) {
}
