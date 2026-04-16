package com.minipaygateway.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.minipaygateway.domain.ReconciliationReport;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, Long> {

	Page<ReconciliationReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
