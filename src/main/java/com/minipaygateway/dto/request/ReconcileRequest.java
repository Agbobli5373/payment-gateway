package com.minipaygateway.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReconcileRequest(@NotNull Instant from, @NotNull Instant to,
		@NotBlank @Size(min = 3, max = 3) String currency) {
}
