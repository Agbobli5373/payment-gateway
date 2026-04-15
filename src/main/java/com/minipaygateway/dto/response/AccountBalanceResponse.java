package com.minipaygateway.dto.response;

import java.time.Instant;

public record AccountBalanceResponse(long accountId, String currency, String balance, Instant asOf) {
}
