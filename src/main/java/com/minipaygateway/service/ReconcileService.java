package com.minipaygateway.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.domain.LedgerEntry;
import com.minipaygateway.domain.PaymentTransaction;
import com.minipaygateway.domain.ReconciliationReport;
import com.minipaygateway.domain.enums.DiscrepancyType;
import com.minipaygateway.domain.enums.PaymentStatus;
import com.minipaygateway.domain.enums.ReconciliationRunStatus;
import com.minipaygateway.dto.response.ReconciliationDiscrepancyResponse;
import com.minipaygateway.dto.response.ReconciliationReportDetailResponse;
import com.minipaygateway.dto.response.ReconciliationReportSummaryResponse;
import com.minipaygateway.exception.ReconciliationReportCorruptException;
import com.minipaygateway.exception.ReconciliationReportNotFoundException;
import com.minipaygateway.repository.LedgerEntryRepository;
import com.minipaygateway.repository.PaymentTransactionRepository;
import com.minipaygateway.repository.ReconciliationReportRepository;

@Service
public class ReconcileService {

	private static final ZoneId UTC = ZoneId.of("UTC");

	private static final String REF_PROCESS = "PROCESS";

	private static final String REF_SETTLE = "SETTLE";

	private final PaymentTransactionRepository paymentTransactionRepository;

	private final LedgerEntryRepository ledgerEntryRepository;

	private final ReconciliationReportRepository reconciliationReportRepository;

	private final ObjectMapper objectMapper;

	public ReconcileService(PaymentTransactionRepository paymentTransactionRepository,
			LedgerEntryRepository ledgerEntryRepository, ReconciliationReportRepository reconciliationReportRepository,
			ObjectMapper objectMapper) {
		this.paymentTransactionRepository = paymentTransactionRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.reconciliationReportRepository = reconciliationReportRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public Page<ReconciliationReportSummaryResponse> listReports(Pageable pageable) {
		return reconciliationReportRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toSummary);
	}

	@Transactional(readOnly = true)
	public ReconciliationReportDetailResponse getReport(long id) {
		ReconciliationReport r = reconciliationReportRepository.findById(id)
				.orElseThrow(() -> new ReconciliationReportNotFoundException(id));
		return toDetail(r);
	}

	@Transactional
	public ReconciliationReportDetailResponse runReconciliation(Instant fromInclusive, Instant toInclusive,
			String currency) {
		if (fromInclusive.isAfter(toInclusive)) {
			throw new IllegalArgumentException("from must be less than or equal to to");
		}
		String ccy = currency.toUpperCase();
		List<PaymentTransaction> settled = paymentTransactionRepository.findSettledForReconciliation(
				PaymentStatus.SETTLED, ccy, fromInclusive, toInclusive);
		Set<Long> settledIds = settled.stream().map(PaymentTransaction::getId).collect(Collectors.toCollection(HashSet::new));
		Map<Long, List<LedgerEntry>> ledgerByTxn = new HashMap<>();
		if (!settledIds.isEmpty()) {
			for (LedgerEntry e : ledgerEntryRepository.findByPaymentTransactionIdIn(settledIds)) {
				Long tid = e.getPaymentTransactionId();
				if (tid != null) {
					ledgerByTxn.computeIfAbsent(tid, k -> new ArrayList<>()).add(e);
				}
			}
			for (List<LedgerEntry> list : ledgerByTxn.values()) {
				list.sort(Comparator.comparing(LedgerEntry::getId));
			}
		}

		List<ReconciliationDiscrepancyResponse> discrepancies = new ArrayList<>();
		for (PaymentTransaction p : settled) {
			evaluateSettledPayment(p, ledgerByTxn.getOrDefault(p.getId(), List.of()), discrepancies);
		}

		List<LedgerEntry> orphans = ledgerEntryRepository.findOrphanedPaymentLedgerInWindow(ccy, fromInclusive,
				toInclusive, fromInclusive, toInclusive);
		Set<Long> orphanPaymentIds = orphans.stream()
				.map(LedgerEntry::getPaymentTransactionId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, String> paymentRefById = paymentTransactionRepository.findAllById(orphanPaymentIds).stream()
				.collect(Collectors.toMap(PaymentTransaction::getId, PaymentTransaction::getReference));
		for (LedgerEntry e : orphans) {
			String paymentRef = e.getPaymentTransactionId() == null ? null
					: paymentRefById.get(e.getPaymentTransactionId());
			discrepancies.add(new ReconciliationDiscrepancyResponse(DiscrepancyType.ORPHANED_LEDGER_ENTRY,
					e.getPaymentTransactionId(), paymentRef, e.getId(), e.getReference(), null, e.getAmount(),
					"Payment ledger row is not fully aligned with a SETTLED payment in this period and currency"));
		}

		int expectedCount = settled.size();
		BigDecimal expectedSum = settled.stream()
				.map(PaymentTransaction::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		int actualCount = settledIds.stream()
				.mapToInt(id -> reconcileShapeEntries(ledgerByTxn.getOrDefault(id, List.of())).size())
				.sum();
		BigDecimal actualSum = settledIds.stream()
				.flatMap(id -> reconcileShapeEntries(ledgerByTxn.getOrDefault(id, List.of())).stream())
				.map(LedgerEntry::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		ReconciliationRunStatus runStatus = discrepancies.isEmpty() ? ReconciliationRunStatus.SUCCESS
				: ReconciliationRunStatus.COMPLETED_WITH_DISCREPANCIES;
		String json;
		try {
			json = objectMapper.writeValueAsString(discrepancies);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}

		ReconciliationReport entity = new ReconciliationReport();
		entity.setPeriodStart(fromInclusive.atZone(UTC).toLocalDate());
		entity.setPeriodEnd(toInclusive.atZone(UTC).toLocalDate());
		entity.setReconcileFrom(fromInclusive);
		entity.setReconcileTo(toInclusive);
		entity.setCurrency(ccy);
		entity.setDiscrepancyCount(discrepancies.size());
		entity.setStatus(runStatus);
		entity.setExpectedCount(expectedCount);
		entity.setExpectedSum(expectedSum.setScale(4, RoundingMode.HALF_UP));
		entity.setActualCount(actualCount);
		entity.setActualSum(actualSum.setScale(4, RoundingMode.HALF_UP));
		entity.setDiscrepancyDetailsJson(json);
		entity.setCompletedAt(Instant.now());
		entity = reconciliationReportRepository.save(entity);
		return toDetail(entity);
	}

	private void evaluateSettledPayment(PaymentTransaction p, List<LedgerEntry> entries,
			List<ReconciliationDiscrepancyResponse> discrepancies) {
		if (entries.isEmpty()) {
			discrepancies.add(new ReconciliationDiscrepancyResponse(DiscrepancyType.MISSING_LEDGER_ENTRY, p.getId(),
					p.getReference(), null, null, p.getAmount(), BigDecimal.ZERO,
					"Settled payment has no ledger entries"));
			return;
		}
		long process = entries.stream().filter(e -> REF_PROCESS.equals(e.getReference())).count();
		long settle = entries.stream().filter(e -> REF_SETTLE.equals(e.getReference())).count();
		List<LedgerEntry> processAndSettle = entries.stream()
				.filter(e -> REF_PROCESS.equals(e.getReference()) || REF_SETTLE.equals(e.getReference()))
				.toList();
		boolean shapeOk = process == 1 && settle == 1;
		boolean amountsMatch = processAndSettle.stream().allMatch(e -> e.getAmount().compareTo(p.getAmount()) == 0);
		BigDecimal expectedPairSum = p.getAmount().multiply(new BigDecimal("2"));
		BigDecimal sumProcessSettle = processAndSettle.stream()
				.map(LedgerEntry::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (!shapeOk) {
			discrepancies.add(new ReconciliationDiscrepancyResponse(DiscrepancyType.AMOUNT_MISMATCH, p.getId(),
					p.getReference(), null, null, expectedPairSum, sumProcessSettle,
					"Expected exactly one PROCESS and one SETTLE row; found process=%d, settle=%d".formatted(process,
							settle)));
		}
		else if (!amountsMatch) {
			discrepancies.add(new ReconciliationDiscrepancyResponse(DiscrepancyType.AMOUNT_MISMATCH, p.getId(),
					p.getReference(), null, null, expectedPairSum, sumProcessSettle,
					"PROCESS and SETTLE amounts must each match the payment amount %s".formatted(p.getAmount())));
		}
	}

	private static List<LedgerEntry> reconcileShapeEntries(List<LedgerEntry> entries) {
		return entries.stream()
				.filter(e -> REF_PROCESS.equals(e.getReference()) || REF_SETTLE.equals(e.getReference()))
				.toList();
	}

	private ReconciliationReportSummaryResponse toSummary(ReconciliationReport r) {
		return new ReconciliationReportSummaryResponse(r.getId(), r.getPeriodStart(), r.getPeriodEnd(),
				r.getReconcileFrom(), r.getReconcileTo(), r.getCurrency(), r.getDiscrepancyCount(), r.getStatus(),
				r.getExpectedCount(), r.getExpectedSum(), r.getActualCount(), r.getActualSum(), r.getCompletedAt(),
				r.getCreatedAt());
	}

	private ReconciliationReportDetailResponse toDetail(ReconciliationReport r) {
		List<ReconciliationDiscrepancyResponse> items;
		try {
			items = objectMapper.readValue(r.getDiscrepancyDetailsJson(), new TypeReference<>() {
			});
		}
		catch (JsonProcessingException e) {
			throw new ReconciliationReportCorruptException(r.getId(), e);
		}
		return new ReconciliationReportDetailResponse(r.getId(), r.getPeriodStart(), r.getPeriodEnd(),
				r.getReconcileFrom(), r.getReconcileTo(), r.getCurrency(), r.getDiscrepancyCount(), r.getStatus(),
				r.getExpectedCount(), r.getExpectedSum(), r.getActualCount(), r.getActualSum(), r.getCompletedAt(),
				r.getCreatedAt(), items);
	}
}
