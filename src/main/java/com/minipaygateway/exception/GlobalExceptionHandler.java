package com.minipaygateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
