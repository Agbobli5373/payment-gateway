package com.minipaygateway.exception;

public class TransactionNotFoundException extends RuntimeException {

	public TransactionNotFoundException(String reference) {
		super("Payment transaction not found: " + reference);
	}
}
