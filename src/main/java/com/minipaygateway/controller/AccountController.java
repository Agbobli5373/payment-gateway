package com.minipaygateway.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.dto.request.CreateAccountRequest;
import com.minipaygateway.dto.response.AccountBalanceResponse;
import com.minipaygateway.dto.response.CreateAccountResponse;
import com.minipaygateway.filter.IdempotencyKeyHeaderFilter;
import com.minipaygateway.service.AccountService;
import com.minipaygateway.service.IdempotencyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
@SecurityRequirement(name = "bearer-jwt")
public class AccountController {

	private final AccountService accountService;
	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;
	private final Validator validator;

	public AccountController(AccountService accountService, IdempotencyService idempotencyService,
			ObjectMapper objectMapper, Validator validator) {
		this.accountService = accountService;
		this.idempotencyService = idempotencyService;
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	@Operation(summary = "Create account (ADMIN)")
	@Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Client UUID; duplicate key + same body replays the stored response (201)")
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<CreateAccountResponse> create(
			@RequestHeader(IdempotencyKeyHeaderFilter.HEADER) UUID idempotencyKey,
			@RequestBody String rawBody) throws JsonProcessingException {
		String hash = IdempotencyService.sha256Hex(rawBody);
		CreateAccountRequest request = objectMapper.readValue(rawBody, CreateAccountRequest.class);
		var violations = validator.validate(request);
		if (!violations.isEmpty()) {
			throw new ConstraintViolationException(violations);
		}
		var result = idempotencyService.execute(idempotencyKey, hash, () -> accountService.create(request));
		CreateAccountResponse body = objectMapper.readValue(result.responseBodyJson(), CreateAccountResponse.class);
		return ResponseEntity.status(result.httpStatus()).body(body);
	}

	@Operation(summary = "Get live balance (MERCHANT or ADMIN)")
	@GetMapping("/{id}/balance")
	@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
	public AccountBalanceResponse balance(@PathVariable long id) {
		return accountService.getBalance(id);
	}
}
