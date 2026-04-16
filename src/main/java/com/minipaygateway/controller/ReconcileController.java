package com.minipaygateway.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minipaygateway.dto.request.ReconcileRequest;
import com.minipaygateway.dto.response.ReconciliationReportDetailResponse;
import com.minipaygateway.dto.response.ReconciliationReportSummaryResponse;
import com.minipaygateway.service.ReconcileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reconcile")
@Tag(name = "Reconciliation")
@SecurityRequirement(name = "bearer-jwt")
public class ReconcileController {

	private static final int MAX_PAGE_SIZE = 100;

	private final ReconcileService reconcileService;

	public ReconcileController(ReconcileService reconcileService) {
		this.reconcileService = reconcileService;
	}

	@Operation(summary = "Run reconciliation (ADMIN or AUDITOR)")
	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
	public ReconciliationReportDetailResponse runReconciliation(@RequestBody @Valid ReconcileRequest request) {
		return reconcileService.runReconciliation(request.from(), request.to(), request.currency());
	}

	@Operation(summary = "List reconciliation reports (ADMIN or AUDITOR)")
	@GetMapping("/reports")
	@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
	public Page<ReconciliationReportSummaryResponse> listReports(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		var pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
		return reconcileService.listReports(pageable);
	}

	@Operation(summary = "Get reconciliation report detail (ADMIN or AUDITOR)")
	@GetMapping("/reports/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
	public ReconciliationReportDetailResponse getReport(@PathVariable long id) {
		return reconcileService.getReport(id);
	}
}
