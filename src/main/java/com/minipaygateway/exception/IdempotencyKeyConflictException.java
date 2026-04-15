package com.minipaygateway.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

	public IdempotencyKeyConflictException() {
		super("Idempotency key was used with a different request body");
	}
}
