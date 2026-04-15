package com.minipaygateway.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.domain.enums.PaymentStatus;
import com.minipaygateway.dto.request.InitiatePaymentRequest;
import com.minipaygateway.dto.response.PaymentTransactionResponse;
import com.minipaygateway.filter.IdempotencyKeyHeaderFilter;
import com.minipaygateway.service.IdempotencyService;
import com.minipaygateway.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
@SecurityRequirement(name = "bearer-jwt")
public class PaymentController {

	private static final int MAX_PAGE_SIZE = 100;

	private static final String IDEMPOTENCY_KEY_DESC = "Fresh UUID per HTTP operation. Reusing the same key on a different path with the same body can replay an unexpected stored response.";

	private final PaymentService paymentService;
	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;
	private final Validator validator;

	public PaymentController(PaymentService paymentService, IdempotencyService idempotencyService,
			ObjectMapper objectMapper, Validator validator) {
		this.paymentService = paymentService;
		this.idempotencyService = idempotencyService;
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	@Operation(summary = "Initiate payment (MERCHANT or ADMIN)")
	@Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_KEY_DESC)
	@PostMapping("/initiate")
	@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
	public ResponseEntity<PaymentTransactionResponse> initiate(
			@RequestHeader(IdempotencyKeyHeaderFilter.HEADER) UUID idempotencyKey, @RequestBody String rawBody)
			throws JsonProcessingException {
		String hash = IdempotencyService.sha256Hex(rawBody);
		InitiatePaymentRequest request = objectMapper.readValue(rawBody, InitiatePaymentRequest.class);
		var violations = validator.validate(request);
		if (!violations.isEmpty()) {
			throw new ConstraintViolationException(violations);
		}
		var result = idempotencyService.execute(idempotencyKey, hash,
				() -> new IdempotencyService.IdempotentResult(201, paymentService.initiate(request)));
		PaymentTransactionResponse body = objectMapper.readValue(result.responseBodyJson(),
				PaymentTransactionResponse.class);
		return ResponseEntity.status(result.httpStatus()).body(body);
	}

	@Operation(summary = "List payments (MERCHANT or ADMIN)")
	@GetMapping
	@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
	public Page<PaymentTransactionResponse> list(@RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) Long payerAccountId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
		return paymentService.list(status, from, to, payerAccountId, pageable);
	}

	@Operation(summary = "Get payment by reference (MERCHANT or ADMIN)")
	@GetMapping("/{ref}")
	@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
	public PaymentTransactionResponse getByRef(@PathVariable("ref") String ref) {
		return paymentService.getByReference(ref);
	}

	@Operation(summary = "Process payment (ADMIN only)")
	@Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_KEY_DESC)
	@PostMapping("/{ref}/process")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PaymentTransactionResponse> process(
			@PathVariable("ref") String ref,
			@RequestHeader(IdempotencyKeyHeaderFilter.HEADER) UUID idempotencyKey, @RequestBody String rawBody)
			throws JsonProcessingException {
		return executeMutation(idempotencyKey, rawBody, () -> paymentService.process(ref));
	}

	@Operation(summary = "Settle payment (ADMIN only)")
	@Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_KEY_DESC)
	@PostMapping("/{ref}/settle")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PaymentTransactionResponse> settle(
			@PathVariable("ref") String ref,
			@RequestHeader(IdempotencyKeyHeaderFilter.HEADER) UUID idempotencyKey, @RequestBody String rawBody)
			throws JsonProcessingException {
		return executeMutation(idempotencyKey, rawBody, () -> paymentService.settle(ref));
	}

	@Operation(summary = "Reverse payment (ADMIN only)",
			description = "May return 422 INSUFFICIENT_BALANCE if the payee (e.g. MERCHANT) cannot fund the compensating debit.")
	@Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_KEY_DESC)
	@PostMapping("/{ref}/reverse")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PaymentTransactionResponse> reverse(
			@PathVariable("ref") String ref,
			@RequestHeader(IdempotencyKeyHeaderFilter.HEADER) UUID idempotencyKey, @RequestBody String rawBody)
			throws JsonProcessingException {
		return executeMutation(idempotencyKey, rawBody, () -> paymentService.reverse(ref));
	}

	@Operation(summary = "Fail pending payment (ADMIN only)")
	@Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_KEY_DESC)
	@PostMapping("/{ref}/fail")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PaymentTransactionResponse> fail(
			@PathVariable("ref") String ref,
			@RequestHeader(IdempotencyKeyHeaderFilter.HEADER) UUID idempotencyKey, @RequestBody String rawBody)
			throws JsonProcessingException {
		return executeMutation(idempotencyKey, rawBody, () -> paymentService.fail(ref));
	}

	private ResponseEntity<PaymentTransactionResponse> executeMutation(UUID idempotencyKey, String rawBody,
			java.util.function.Supplier<PaymentTransactionResponse> action) throws JsonProcessingException {
		String hash = IdempotencyService.sha256Hex(rawBody);
		var result = idempotencyService.execute(idempotencyKey, hash,
				() -> new IdempotencyService.IdempotentResult(200, action.get()));
		PaymentTransactionResponse body = objectMapper.readValue(result.responseBodyJson(),
				PaymentTransactionResponse.class);
		return ResponseEntity.status(result.httpStatus()).body(body);
	}
}
