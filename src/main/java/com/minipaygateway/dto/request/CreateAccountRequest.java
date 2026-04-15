package com.minipaygateway.dto.request;

import java.math.BigDecimal;

import com.minipaygateway.domain.enums.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateAccountRequest(
		@NotBlank String ownerRef,
		@NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be ISO 4217 (3 uppercase letters)") String currency,
		@NotNull AccountType accountType,
		@NotNull @PositiveOrZero BigDecimal initialBalance) {
}
