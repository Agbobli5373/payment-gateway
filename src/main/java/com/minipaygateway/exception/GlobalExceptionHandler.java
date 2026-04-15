package com.minipaygateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

	@ExceptionHandler(IdempotencyKeyConflictException.class)
	ResponseEntity<ProblemDetail> idempotencyConflict(IdempotencyKeyConflictException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setTitle("Conflict");
		problem.setProperty("code", "IDEMPOTENCY_KEY_REUSE");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}
}
