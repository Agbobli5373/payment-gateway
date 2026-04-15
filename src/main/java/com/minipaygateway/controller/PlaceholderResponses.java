package com.minipaygateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Shared 501 responses until Epics 5–6 ship real implementations.
 */
public final class PlaceholderResponses {

	private PlaceholderResponses() {
	}

	public static ResponseEntity<ProblemDetail> notImplemented(String epicLabel) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED,
				"This operation is not implemented yet (" + epicLabel + ").");
		problem.setTitle("Not Implemented");
		problem.setProperty("code", "NOT_IMPLEMENTED");
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(problem);
	}
}
