package com.minipaygateway.exception;

public class ReconciliationReportNotFoundException extends RuntimeException {

	public ReconciliationReportNotFoundException(long id) {
		super("Reconciliation report not found: " + id);
	}
}
