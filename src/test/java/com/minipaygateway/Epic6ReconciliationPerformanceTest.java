package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.minipaygateway.service.ReconcileService;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Epic6ReconciliationPerformanceTest {

	private static final int BENCH_SIZE = 10_000;

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
			.withDatabaseName("paygateway")
			.withUsername("pguser")
			.withPassword("pgsecret");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ReconcileService reconcileService;

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("""
				delete from ledger_entries where transaction_id in (
				  select id from payment_transactions where reference like 'perf-bench-%'
				)
				""");
		jdbcTemplate.update("delete from payment_transactions where reference like 'perf-bench-%'");
		jdbcTemplate.update("delete from accounts where owner_ref in ('perf-payer-bench', 'perf-payee-bench')");
	}

	@Test
	void reconcile_10kSettledPayments_completesWithin30Seconds() {
		jdbcTemplate.update("""
				insert into accounts (owner_ref, currency, account_type)
				values ('perf-payer-bench', 'USD', 'MERCHANT'), ('perf-payee-bench', 'USD', 'MERCHANT')
				""");
		long payerId = jdbcTemplate.queryForObject(
				"select id from accounts where owner_ref = 'perf-payer-bench' and currency = 'USD'", Long.class);
		long payeeId = jdbcTemplate.queryForObject(
				"select id from accounts where owner_ref = 'perf-payee-bench' and currency = 'USD'", Long.class);
		Long floatId = jdbcTemplate.query(
				"select id from accounts where owner_ref = 'SYSTEM_FLOAT' and currency = 'USD'",
				rs -> rs.next() ? rs.getLong(1) : null);
		if (floatId == null) {
			jdbcTemplate.update(
					"insert into accounts (owner_ref, currency, account_type) values ('SYSTEM_FLOAT', 'USD', 'FLOAT')");
			floatId = jdbcTemplate.queryForObject(
					"select id from accounts where owner_ref = 'SYSTEM_FLOAT' and currency = 'USD'", Long.class);
		}

		Instant settled = Instant.parse("2023-05-01T12:00:00Z");
		Timestamp settledTs = Timestamp.from(settled);
		jdbcTemplate.update("""
				insert into payment_transactions (reference, payer_account_id, payee_account_id, amount, currency, status, settled_at, created_at, updated_at)
				select 'perf-bench-' || g, ?, ?, 1.0000, 'USD', 'SETTLED', ?, ?, ?
				from generate_series(1, ?) g
				""", payerId, payeeId, settledTs, settledTs, settledTs, BENCH_SIZE);

		jdbcTemplate.update("""
				insert into ledger_entries (debit_account_id, credit_account_id, amount, currency, transaction_id, reference, created_at)
				select ?, ?, 1.0000, 'USD', p.id, 'PROCESS', ?
				from payment_transactions p where p.reference like 'perf-bench-%'
				""", payerId, floatId, settledTs);
		jdbcTemplate.update("""
				insert into ledger_entries (debit_account_id, credit_account_id, amount, currency, transaction_id, reference, created_at)
				select ?, ?, 1.0000, 'USD', p.id, 'SETTLE', ?
				from payment_transactions p where p.reference like 'perf-bench-%'
				""", floatId, payeeId, settledTs);

		Instant from = Instant.parse("2023-05-01T00:00:00Z");
		Instant to = Instant.parse("2023-05-01T23:59:59.999999999Z");
		long t0 = System.nanoTime();
		var report = reconcileService.runReconciliation(from, to, "USD");
		long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
		assertThat(report.discrepancyCount()).isZero();
		assertThat(elapsedMs).isLessThan(30_000L);
	}
}
