package com.minipaygateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RBAC shell for payment APIs (Epic 4). Business logic in Epic 5.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
@SecurityRequirement(name = "bearer-jwt")
public class PaymentController {

	@Operation(summary = "Initiate payment (MERCHANT or ADMIN)", description = "Epic 5 — returns 501 until implemented.")
	@PostMapping("/initiate")
	@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
	public ResponseEntity<ProblemDetail> initiate() {
		return PlaceholderResponses.notImplemented("Epic 5");
	}

	@Operation(summary = "Get payment (MERCHANT or ADMIN)")
	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
	public ResponseEntity<ProblemDetail> getById(@PathVariable long id) {
		return PlaceholderResponses.notImplemented("Epic 5");
	}

	@Operation(summary = "Process payment (ADMIN only)")
	@PostMapping("/{id}/process")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ProblemDetail> process(@PathVariable long id) {
		return PlaceholderResponses.notImplemented("Epic 5");
	}

	@Operation(summary = "Settle payment (ADMIN only)")
	@PostMapping("/{id}/settle")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ProblemDetail> settle(@PathVariable long id) {
		return PlaceholderResponses.notImplemented("Epic 5");
	}

	@Operation(summary = "Reverse payment (ADMIN only)")
	@PostMapping("/{id}/reverse")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ProblemDetail> reverse(@PathVariable long id) {
		return PlaceholderResponses.notImplemented("Epic 5");
	}
}
