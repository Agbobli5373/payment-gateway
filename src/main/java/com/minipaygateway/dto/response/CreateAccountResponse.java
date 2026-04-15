package com.minipaygateway.dto.response;

import com.minipaygateway.domain.enums.AccountType;

public record CreateAccountResponse(long id, String ownerRef, String currency, AccountType accountType) {
}
