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
 * RBAC shell for reconciliation APIs (Epic 4). Business logic in Epic 6.
 */
@RestController
@RequestMapping("/api/v1/reconcile")
@Tag(name = "Reconciliation")
@SecurityRequirement(name = "bearer-jwt")
public class ReconcileController {

	@Operation(summary = "Run reconciliation (ADMIN or AUDITOR)")
	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
	public ResponseEntity<ProblemDetail> runReconciliation() {
		return PlaceholderResponses.notImplemented("Epic 6");
	}

	@Operation(summary = "Get reconciliation report (ADMIN or AUDITOR)")
	@GetMapping("/reports/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
	public ResponseEntity<ProblemDetail> getReport(@PathVariable long id) {
		return PlaceholderResponses.notImplemented("Epic 6");
	}
}
