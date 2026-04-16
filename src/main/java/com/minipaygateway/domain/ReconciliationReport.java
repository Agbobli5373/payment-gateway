package com.minipaygateway.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.minipaygateway.domain.enums.ReconciliationRunStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "reconciliation_reports")
public class ReconciliationReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Column(name = "reconcile_from")
	private Instant reconcileFrom;

	@Column(name = "reconcile_to")
	private Instant reconcileTo;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "discrepancy_count", nullable = false)
	private int discrepancyCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReconciliationRunStatus status;

	@Column(name = "expected_count", nullable = false)
	private int expectedCount;

	@Column(name = "expected_sum", nullable = false, precision = 19, scale = 4)
	private BigDecimal expectedSum;

	@Column(name = "actual_count", nullable = false)
	private int actualCount;

	@Column(name = "actual_sum", nullable = false, precision = 19, scale = 4)
	private BigDecimal actualSum;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "discrepancy_details", nullable = false)
	private String discrepancyDetailsJson;

	@Column(name = "completed_at", nullable = false)
	private Instant completedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		if (completedAt == null) {
			completedAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public LocalDate getPeriodStart() {
		return periodStart;
	}

	public void setPeriodStart(LocalDate periodStart) {
		this.periodStart = periodStart;
	}

	public LocalDate getPeriodEnd() {
		return periodEnd;
	}

	public void setPeriodEnd(LocalDate periodEnd) {
		this.periodEnd = periodEnd;
	}

	public Instant getReconcileFrom() {
		return reconcileFrom;
	}

	public void setReconcileFrom(Instant reconcileFrom) {
		this.reconcileFrom = reconcileFrom;
	}

	public Instant getReconcileTo() {
		return reconcileTo;
	}

	public void setReconcileTo(Instant reconcileTo) {
		this.reconcileTo = reconcileTo;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public int getDiscrepancyCount() {
		return discrepancyCount;
	}

	public void setDiscrepancyCount(int discrepancyCount) {
		this.discrepancyCount = discrepancyCount;
	}

	public ReconciliationRunStatus getStatus() {
		return status;
	}

	public void setStatus(ReconciliationRunStatus status) {
		this.status = status;
	}

	public int getExpectedCount() {
		return expectedCount;
	}

	public void setExpectedCount(int expectedCount) {
		this.expectedCount = expectedCount;
	}

	public BigDecimal getExpectedSum() {
		return expectedSum;
	}

	public void setExpectedSum(BigDecimal expectedSum) {
		this.expectedSum = expectedSum;
	}

	public int getActualCount() {
		return actualCount;
	}

	public void setActualCount(int actualCount) {
		this.actualCount = actualCount;
	}

	public BigDecimal getActualSum() {
		return actualSum;
	}

	public void setActualSum(BigDecimal actualSum) {
		this.actualSum = actualSum;
	}

	public String getDiscrepancyDetailsJson() {
		return discrepancyDetailsJson;
	}

	public void setDiscrepancyDetailsJson(String discrepancyDetailsJson) {
		this.discrepancyDetailsJson = discrepancyDetailsJson;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
