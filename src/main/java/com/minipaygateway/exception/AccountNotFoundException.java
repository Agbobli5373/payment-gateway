package com.minipaygateway.exception;

public class AccountNotFoundException extends RuntimeException {

	public AccountNotFoundException(long accountId) {
		super("Account not found: " + accountId);
	}
}
