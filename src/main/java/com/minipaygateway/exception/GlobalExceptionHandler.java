package com.minipaygateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				ex.getMessage() != null ? ex.getMessage() : "Forbidden");
		problem.setTitle("Forbidden");
		problem.setProperty("code", "FORBIDDEN");
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
	}

	@ExceptionHandler(AccountNotFoundException.class)
	ResponseEntity<ProblemDetail> accountNotFound(AccountNotFoundException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Not Found");
		problem.setProperty("code", "ACCOUNT_NOT_FOUND");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
	}

	@ExceptionHandler(InsufficientBalanceException.class)
	ResponseEntity<ProblemDetail> insufficientBalance(InsufficientBalanceException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setTitle("Unprocessable Entity");
		problem.setProperty("code", "INSUFFICIENT_BALANCE");
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setTitle("Bad Request");
		problem.setProperty("code", "VALIDATION_ERROR");
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(BadCredentialsException.class)
	ResponseEntity<ProblemDetail> badCredentials() {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		problem.setTitle("Unauthorized");
		problem.setProperty("code", "UNAUTHORIZED");
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Bad Request");
		problem.setProperty("code", "VALIDATION_ERROR");
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ProblemDetail> constraintViolation(ConstraintViolationException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setTitle("Bad Request");
		problem.setProperty("code", "VALIDATION_ERROR");
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(JsonProcessingException.class)
	ResponseEntity<ProblemDetail> jsonProcessing(JsonProcessingException ex) {
		String detail = ex.getOriginalMessage() != null ? ex.getOriginalMessage() : "Invalid JSON";
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("Bad Request");
		problem.setProperty("code", "VALIDATION_ERROR");
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(IdempotencyKeyConflictException.class)
	ResponseEntity<ProblemDetail> idempotencyConflict(IdempotencyKeyConflictException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setTitle("Conflict");
		problem.setProperty("code", "IDEMPOTENCY_CONFLICT");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(TransactionNotFoundException.class)
	ResponseEntity<ProblemDetail> transactionNotFound(TransactionNotFoundException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Not Found");
		problem.setProperty("code", "TRANSACTION_NOT_FOUND");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
	}

	@ExceptionHandler(InvalidStateTransitionException.class)
	ResponseEntity<ProblemDetail> invalidStateTransition(InvalidStateTransitionException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setTitle("Unprocessable Entity");
		problem.setProperty("code", "INVALID_STATE_TRANSITION");
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
	}

	@ExceptionHandler(ReconciliationReportNotFoundException.class)
	ResponseEntity<ProblemDetail> reconciliationReportNotFound(ReconciliationReportNotFoundException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Not Found");
		problem.setProperty("code", "RECONCILIATION_REPORT_NOT_FOUND");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
	}

	@ExceptionHandler(ReconciliationReportCorruptException.class)
	ResponseEntity<ProblemDetail> reconciliationReportCorrupt(ReconciliationReportCorruptException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
		problem.setTitle("Internal Server Error");
		problem.setProperty("code", "RECONCILIATION_REPORT_CORRUPT");
		problem.setProperty("reportId", ex.getReportId());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
	}

	@ExceptionHandler({ CannotCreateTransactionException.class, CannotGetJdbcConnectionException.class })
	ResponseEntity<ProblemDetail> serviceUnavailable(RuntimeException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Database temporarily unavailable");
		problem.setTitle("Service Unavailable");
		problem.setProperty("code", "SERVICE_UNAVAILABLE");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header("Retry-After", "5")
				.body(problem);
	}
}
