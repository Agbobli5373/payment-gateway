package com.minipaygateway.scheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.minipaygateway.service.ReconcileService;

@Component
public class DailyReconcileJob {

	private static final Logger log = LoggerFactory.getLogger(DailyReconcileJob.class);

	private static final ZoneId UTC = ZoneId.of("UTC");

	private final ReconcileService reconcileService;

	private final List<String> currencies;

	public DailyReconcileJob(ReconcileService reconcileService,
			@Value("${app.reconcile.currencies:USD,EUR,GHS}") String currenciesCsv) {
		this.reconcileService = reconcileService;
		this.currencies = Arrays.stream(currenciesCsv.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}

	@Scheduled(cron = "${app.reconcile.cron}")
	public void reconcilePriorUtcDay() {
		LocalDate day = LocalDate.now(UTC).minusDays(1);
		Instant from = day.atStartOfDay(UTC).toInstant();
		Instant to = day.plusDays(1).atStartOfDay(UTC).toInstant().minusNanos(1);
		for (String ccy : currencies) {
			try {
				var report = reconcileService.runReconciliation(from, to, ccy);
				if (report.discrepancyCount() > 0) {
					log.warn("Daily reconciliation for {} on {} found {} discrepancies (report id {})", ccy, day,
							report.discrepancyCount(), report.id());
				}
			}
			catch (RuntimeException ex) {
				log.error("Daily reconciliation failed for currency {}", ccy, ex);
			}
		}
	}
}
