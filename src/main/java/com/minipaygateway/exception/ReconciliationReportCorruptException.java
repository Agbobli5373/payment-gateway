package com.minipaygateway.exception;

public class ReconciliationReportCorruptException extends RuntimeException {

	private final long reportId;

	public ReconciliationReportCorruptException(long reportId, Throwable cause) {
		super("Reconciliation report " + reportId + " has unreadable discrepancy payload", cause);
		this.reportId = reportId;
	}

	public long getReportId() {
		return reportId;
	}
}
