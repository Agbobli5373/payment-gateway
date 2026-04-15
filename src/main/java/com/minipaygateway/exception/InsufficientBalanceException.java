package com.minipaygateway.exception;

public class InsufficientBalanceException extends RuntimeException {

	public InsufficientBalanceException() {
		super("Insufficient balance for this debit");
	}
}
