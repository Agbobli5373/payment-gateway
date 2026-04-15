package com.minipaygateway.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InitiatePaymentRequest(
		@NotNull Long payerAccountId,
		@NotNull Long payeeAccountId,
		@NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount,
		@NotBlank @Size(min = 3, max = 3) String currency) {
}
