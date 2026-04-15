package com.minipaygateway.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipaygateway.domain.Account;
import com.minipaygateway.domain.LedgerEntry;
import com.minipaygateway.domain.enums.AccountType;
import com.minipaygateway.exception.InsufficientBalanceException;
import com.minipaygateway.repository.AccountRepository;
import com.minipaygateway.repository.LedgerEntryRepository;

@Service
public class LedgerService {

	private static final int AMOUNT_SCALE = 4;

	private final AccountRepository accountRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	public LedgerService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
		this.accountRepository = accountRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	@Transactional
	public LedgerEntry postBalancedEntry(long debitAccountId, long creditAccountId, BigDecimal amount,
			String currency, String reference, Long paymentTransactionId) {
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		amount = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
		String ccy = currency.toUpperCase();

		Account debitAccount = loadDebitAccountForUpdate(debitAccountId);
		Account creditAccount = accountRepository.findById(creditAccountId)
				.orElseThrow(() -> new IllegalArgumentException("Credit account not found"));

		if (!debitAccount.getCurrency().equalsIgnoreCase(ccy) || !creditAccount.getCurrency().equalsIgnoreCase(ccy)) {
			throw new IllegalArgumentException("Account currency must match posting currency");
		}

		if (requiresNonNegativeBalance(debitAccount.getAccountType())) {
			BigDecimal net = ledgerEntryRepository.computeNetBalance(debitAccountId, ccy);
			if (net == null) {
				net = BigDecimal.ZERO;
			}
			if (net.subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
				throw new InsufficientBalanceException();
			}
		}

		var entry = new LedgerEntry();
		entry.setDebitAccount(debitAccount);
		entry.setCreditAccount(creditAccount);
		entry.setAmount(amount);
		entry.setCurrency(ccy);
		entry.setReference(reference);
		entry.setPaymentTransactionId(paymentTransactionId);
		return ledgerEntryRepository.save(entry);
	}

	private Account loadDebitAccountForUpdate(long debitAccountId) {
		Account probe = accountRepository.findById(debitAccountId)
				.orElseThrow(() -> new IllegalArgumentException("Debit account not found"));
		if (requiresNonNegativeBalance(probe.getAccountType())) {
			return accountRepository.findByIdForUpdate(debitAccountId)
					.orElseThrow(() -> new IllegalArgumentException("Debit account not found"));
		}
		return probe;
	}

	private boolean requiresNonNegativeBalance(AccountType type) {
		return type == AccountType.MERCHANT || type == AccountType.FEE_POOL;
	}

	@Transactional(readOnly = true)
	public BigDecimal getNetBalance(long accountId, String currency) {
		BigDecimal net = ledgerEntryRepository.computeNetBalance(accountId, currency.toUpperCase());
		return net == null ? BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP) : net;
	}
}
