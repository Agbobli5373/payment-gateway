package com.minipaygateway.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minipaygateway.domain.enums.PaymentStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentTransactionResponse(
		String reference,
		PaymentStatus status,
		long payerAccountId,
		long payeeAccountId,
		BigDecimal amount,
		String currency,
		Instant createdAt,
		Instant updatedAt,
		Instant settledAt,
		Instant failedAt,
		Instant reversedAt,
		List<Long> ledgerEntryIds) {

	public PaymentTransactionResponse {
		ledgerEntryIds = ledgerEntryIds == null ? List.of() : List.copyOf(ledgerEntryIds);
	}
}
