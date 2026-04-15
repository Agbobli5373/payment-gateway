package com.minipaygateway.service;

import com.minipaygateway.domain.enums.PaymentStatus;
import com.minipaygateway.exception.InvalidStateTransitionException;

public final class PaymentStateMachine {

	private PaymentStateMachine() {
	}

	public static void assertCanProcess(PaymentStatus current) {
		if (current != PaymentStatus.PENDING) {
			throw new InvalidStateTransitionException(current, "process");
		}
	}

	public static void assertCanSettle(PaymentStatus current) {
		if (current != PaymentStatus.PROCESSING) {
			throw new InvalidStateTransitionException(current, "settle");
		}
	}

	public static void assertCanReverse(PaymentStatus current) {
		if (current != PaymentStatus.SETTLED) {
			throw new InvalidStateTransitionException(current, "reverse");
		}
	}

	public static void assertCanFail(PaymentStatus current) {
		if (current != PaymentStatus.PENDING) {
			throw new InvalidStateTransitionException(current, "fail");
		}
	}
}
