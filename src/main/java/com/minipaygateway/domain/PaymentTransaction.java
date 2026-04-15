package com.minipaygateway.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.minipaygateway.domain.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64, unique = true, updatable = false)
	private String reference;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "payer_account_id", nullable = false, updatable = false)
	private Account payerAccount;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "payee_account_id", nullable = false, updatable = false)
	private Account payeeAccount;

	@Column(nullable = false, precision = 19, scale = 4, updatable = false)
	private BigDecimal amount;

	@Column(nullable = false, length = 3, updatable = false)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PaymentStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "settled_at")
	private Instant settledAt;

	@Column(name = "failed_at")
	private Instant failedAt;

	@Column(name = "reversed_at")
	private Instant reversedAt;

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public Account getPayerAccount() {
		return payerAccount;
	}

	public void setPayerAccount(Account payerAccount) {
		this.payerAccount = payerAccount;
	}

	public Account getPayeeAccount() {
		return payeeAccount;
	}

	public void setPayeeAccount(Account payeeAccount) {
		this.payeeAccount = payeeAccount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public void setStatus(PaymentStatus status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getSettledAt() {
		return settledAt;
	}

	public void setSettledAt(Instant settledAt) {
		this.settledAt = settledAt;
	}

	public Instant getFailedAt() {
		return failedAt;
	}

	public void setFailedAt(Instant failedAt) {
		this.failedAt = failedAt;
	}

	public Instant getReversedAt() {
		return reversedAt;
	}

	public void setReversedAt(Instant reversedAt) {
		this.reversedAt = reversedAt;
	}
}
