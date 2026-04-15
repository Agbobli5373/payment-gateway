package com.minipaygateway.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipaygateway.domain.Account;
import com.minipaygateway.domain.enums.AccountType;
import com.minipaygateway.dto.request.CreateAccountRequest;
import com.minipaygateway.dto.response.AccountBalanceResponse;
import com.minipaygateway.dto.response.CreateAccountResponse;
import com.minipaygateway.repository.AccountRepository;

@Service
public class AccountService {

	public static final String SYSTEM_SUSPENSE_OWNER = "SYSTEM_SUSPENSE";

	private final AccountRepository accountRepository;
	private final LedgerService ledgerService;

	public AccountService(AccountRepository accountRepository, LedgerService ledgerService) {
		this.accountRepository = accountRepository;
		this.ledgerService = ledgerService;
	}

	@Transactional
	public CreateAccountResponse create(CreateAccountRequest request) {
		Account suspense = getOrCreateSystemSuspense(request.currency());

		Account account = new Account();
		account.setOwnerRef(request.ownerRef());
		account.setCurrency(request.currency().toUpperCase());
		account.setAccountType(request.accountType());
		account = accountRepository.save(account);

		if (request.initialBalance().compareTo(BigDecimal.ZERO) > 0) {
			ledgerService.postBalancedEntry(
					suspense.getId(),
					account.getId(),
					request.initialBalance(),
					account.getCurrency(),
					"OPENING_BALANCE",
					null);
		}

		return new CreateAccountResponse(account.getId(), account.getOwnerRef(), account.getCurrency(),
				account.getAccountType());
	}

	@Transactional(readOnly = true)
	public AccountBalanceResponse getBalance(long accountId) {
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new IllegalArgumentException("Account not found"));
		BigDecimal net = ledgerService.getNetBalance(accountId, account.getCurrency());
		String balanceStr = net.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
		return new AccountBalanceResponse(accountId, account.getCurrency(), balanceStr, Instant.now());
	}

	private Account getOrCreateSystemSuspense(String currency) {
		String ccy = currency.toUpperCase();
		return accountRepository.findByOwnerRefAndCurrency(SYSTEM_SUSPENSE_OWNER, ccy).orElseGet(() -> {
			Account a = new Account();
			a.setOwnerRef(SYSTEM_SUSPENSE_OWNER);
			a.setCurrency(ccy);
			a.setAccountType(AccountType.SUSPENSE);
			return accountRepository.save(a);
		});
	}
}
