package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.minipaygateway.domain.enums.AccountType;
import com.minipaygateway.dto.request.CreateAccountRequest;
import com.minipaygateway.exception.InsufficientBalanceException;
import com.minipaygateway.service.AccountService;
import com.minipaygateway.service.LedgerService;

@SpringBootTest
@Testcontainers
class LedgerServiceConcurrentDebitTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
			.withDatabaseName("paygateway")
			.withUsername("pguser")
			.withPassword("pgsecret");

	@Autowired
	AccountService accountService;

	@Autowired
	LedgerService ledgerService;

	@Test
	void concurrentDebitsFromSameMerchant_oneFailsInsufficientBalance() throws Exception {
		var merchantReq = new CreateAccountRequest("m1", "USD", AccountType.MERCHANT, new BigDecimal("100.00"));
		long merchantId = accountService.create(merchantReq).id();

		var floatReq = new CreateAccountRequest("float1", "USD", AccountType.FLOAT, BigDecimal.ZERO);
		long floatId = accountService.create(floatReq).id();

		var start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);

		AtomicInteger insufficient = new AtomicInteger();
		AtomicInteger success = new AtomicInteger();

		Future<?> f1 = pool.submit(runDebit(start, merchantId, floatId, insufficient, success));
		Future<?> f2 = pool.submit(runDebit(start, merchantId, floatId, insufficient, success));

		start.countDown();
		f1.get();
		f2.get();
		pool.shutdown();

		assertThat(success.get()).isEqualTo(1);
		assertThat(insufficient.get()).isEqualTo(1);

		BigDecimal remaining = ledgerService.getNetBalance(merchantId, "USD");
		assertThat(remaining).isEqualByComparingTo(new BigDecimal("40.0000"));
	}

	private Runnable runDebit(CountDownLatch start, long merchantId, long floatId, AtomicInteger insufficient,
			AtomicInteger success) {
		return () -> {
			try {
				start.await();
				try {
					ledgerService.postBalancedEntry(merchantId, floatId, new BigDecimal("60.00"), "USD", "TEST", null);
					success.incrementAndGet();
				}
				catch (InsufficientBalanceException e) {
					insufficient.incrementAndGet();
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		};
	}
}
