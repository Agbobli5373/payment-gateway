package com.minipaygateway.exception;

import com.minipaygateway.domain.enums.PaymentStatus;

public class InvalidStateTransitionException extends RuntimeException {

	public InvalidStateTransitionException(PaymentStatus current, String operation) {
		super("Invalid transition from " + current + " for " + operation);
	}
}
