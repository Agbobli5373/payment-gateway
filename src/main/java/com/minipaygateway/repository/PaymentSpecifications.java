package com.minipaygateway.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.minipaygateway.domain.PaymentTransaction;
import com.minipaygateway.domain.enums.PaymentStatus;

import jakarta.persistence.criteria.Predicate;

public final class PaymentSpecifications {

	private PaymentSpecifications() {
	}

	public static Specification<PaymentTransaction> filtered(PaymentStatus status, Instant from, Instant to,
			Long payerAccountId, String merchantOwnerRefOrNull) {
		return (root, query, cb) -> {
			List<Predicate> preds = new ArrayList<>();
			if (status != null) {
				preds.add(cb.equal(root.get("status"), status));
			}
			if (from != null) {
				preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
			}
			if (payerAccountId != null) {
				preds.add(cb.equal(root.get("payerAccount").get("id"), payerAccountId));
			}
			if (merchantOwnerRefOrNull != null) {
				preds.add(cb.equal(root.get("payerAccount").get("ownerRef"), merchantOwnerRefOrNull));
			}
			if (preds.isEmpty()) {
				return cb.conjunction();
			}
			return cb.and(preds.toArray(Predicate[]::new));
		};
	}
}
