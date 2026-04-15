package com.minipaygateway.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipaygateway.domain.Account;
import com.minipaygateway.domain.LedgerEntry;
import com.minipaygateway.domain.PaymentTransaction;
import com.minipaygateway.domain.enums.AccountType;
import com.minipaygateway.domain.enums.PaymentStatus;
import com.minipaygateway.dto.request.InitiatePaymentRequest;
import com.minipaygateway.dto.response.PaymentTransactionResponse;
import com.minipaygateway.exception.InsufficientBalanceException;
import com.minipaygateway.exception.TransactionNotFoundException;
import com.minipaygateway.repository.AccountRepository;
import com.minipaygateway.repository.LedgerEntryRepository;
import com.minipaygateway.repository.PaymentSpecifications;
import com.minipaygateway.repository.PaymentTransactionRepository;
import com.minipaygateway.security.JwtUserDetails;

@Service
public class PaymentService {

	private static final int AMOUNT_SCALE = 4;

	private final PaymentTransactionRepository paymentTransactionRepository;
	private final AccountRepository accountRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final LedgerService ledgerService;
	private final AccountService accountService;

	public PaymentService(PaymentTransactionRepository paymentTransactionRepository,
			AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository,
			LedgerService ledgerService, AccountService accountService) {
		this.paymentTransactionRepository = paymentTransactionRepository;
		this.accountRepository = accountRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.ledgerService = ledgerService;
		this.accountService = accountService;
	}

	@Transactional
	public PaymentTransactionResponse initiate(InitiatePaymentRequest request) {
		Account payee = accountRepository.findById(request.payeeAccountId())
				.orElseThrow(() -> new IllegalArgumentException("Payee account not found"));
		Account payer = accountRepository.findByIdForUpdate(request.payerAccountId())
				.orElseThrow(() -> new IllegalArgumentException("Payer account not found"));

		if (payer.getId().equals(payee.getId())) {
			throw new IllegalArgumentException("Payer and payee must differ");
		}
		if (payer.getAccountType() != AccountType.MERCHANT) {
			throw new IllegalArgumentException("Payer must be a MERCHANT account");
		}
		if (!payer.getCurrency().equalsIgnoreCase(payee.getCurrency())) {
			throw new IllegalArgumentException("Payer and payee currency must match");
		}
		String ccy = payer.getCurrency().toUpperCase();
		if (!request.currency().equalsIgnoreCase(ccy)) {
			throw new IllegalArgumentException("currency must match payer account");
		}

		BigDecimal amount = request.amount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}

		assertCanAccessPayer(payer);

		BigDecimal net = ledgerService.getNetBalance(payer.getId(), ccy);
		if (net.compareTo(amount) < 0) {
			throw new InsufficientBalanceException();
		}

		PaymentTransaction p = new PaymentTransaction();
		p.setReference(newReference());
		p.setPayerAccount(payer);
		p.setPayeeAccount(payee);
		p.setAmount(amount);
		p.setCurrency(ccy);
		p.setStatus(PaymentStatus.PENDING);
		p = paymentTransactionRepository.save(p);
		return toResponse(p, List.of());
	}

	@Transactional
	public PaymentTransactionResponse process(String reference) {
		PaymentTransaction p = paymentTransactionRepository.findByReferenceForUpdate(reference)
				.orElseThrow(() -> new TransactionNotFoundException(reference));
		PaymentStateMachine.assertCanProcess(p.getStatus());

		Account floatAcc = accountService.getOrCreateSystemFloat(p.getCurrency());
		ledgerService.postBalancedEntry(p.getPayerAccount().getId(), floatAcc.getId(), p.getAmount(), p.getCurrency(),
				"PROCESS", p.getId());

		p.setStatus(PaymentStatus.PROCESSING);
		p = paymentTransactionRepository.save(p);
		return toResponse(p, ledgerIds(p.getId()));
	}

	@Transactional
	public PaymentTransactionResponse settle(String reference) {
		PaymentTransaction p = paymentTransactionRepository.findByReferenceForUpdate(reference)
				.orElseThrow(() -> new TransactionNotFoundException(reference));
		PaymentStateMachine.assertCanSettle(p.getStatus());

		Account floatAcc = accountService.getOrCreateSystemFloat(p.getCurrency());
		ledgerService.postBalancedEntry(floatAcc.getId(), p.getPayeeAccount().getId(), p.getAmount(), p.getCurrency(),
				"SETTLE", p.getId());

		p.setStatus(PaymentStatus.SETTLED);
		p.setSettledAt(Instant.now());
		p = paymentTransactionRepository.save(p);
		return toResponse(p, ledgerIds(p.getId()));
	}

	/**
	 * Compensating debit on the payee may return 422 {@code INSUFFICIENT_BALANCE} if the payee is a MERCHANT
	 * account that no longer has available funds.
	 */
	@Transactional
	public PaymentTransactionResponse reverse(String reference) {
		PaymentTransaction p = paymentTransactionRepository.findByReferenceForUpdate(reference)
				.orElseThrow(() -> new TransactionNotFoundException(reference));
		PaymentStateMachine.assertCanReverse(p.getStatus());

		Account floatAcc = accountService.getOrCreateSystemFloat(p.getCurrency());
		ledgerService.postBalancedEntry(p.getPayeeAccount().getId(), floatAcc.getId(), p.getAmount(), p.getCurrency(),
				"REVERSE", p.getId());

		p.setStatus(PaymentStatus.REVERSED);
		p.setReversedAt(Instant.now());
		p = paymentTransactionRepository.save(p);
		return toResponse(p, ledgerIds(p.getId()));
	}

	@Transactional
	public PaymentTransactionResponse fail(String reference) {
		PaymentTransaction p = paymentTransactionRepository.findByReferenceForUpdate(reference)
				.orElseThrow(() -> new TransactionNotFoundException(reference));
		PaymentStateMachine.assertCanFail(p.getStatus());

		p.setStatus(PaymentStatus.FAILED);
		p.setFailedAt(Instant.now());
		p = paymentTransactionRepository.save(p);
		return toResponse(p, ledgerIds(p.getId()));
	}

	@Transactional(readOnly = true)
	public Page<PaymentTransactionResponse> list(PaymentStatus status, Instant from, Instant to, Long payerAccountId,
			Pageable pageable) {
		String merchantOwner = merchantScopeOwnerRefOrNull();
		Specification<PaymentTransaction> spec = PaymentSpecifications.filtered(status, from, to, payerAccountId,
				merchantOwner);
		return paymentTransactionRepository.findAll(spec, pageable).map(tx -> toResponse(tx, List.of()));
	}

	@Transactional(readOnly = true)
	public PaymentTransactionResponse getByReference(String reference) {
		PaymentTransaction p = paymentTransactionRepository.findByReference(reference)
				.orElseThrow(() -> new TransactionNotFoundException(reference));
		assertCanView(p);
		return toResponse(p, ledgerIds(p.getId()));
	}

	private List<Long> ledgerIds(long paymentTransactionId) {
		return ledgerEntryRepository.findByPaymentTransactionIdOrderByIdAsc(paymentTransactionId).stream()
				.map(LedgerEntry::getId)
				.toList();
	}

	private PaymentTransactionResponse toResponse(PaymentTransaction p, List<Long> ledgerEntryIds) {
		return new PaymentTransactionResponse(p.getReference(), p.getStatus(), p.getPayerAccount().getId(),
				p.getPayeeAccount().getId(), p.getAmount(), p.getCurrency(), p.getCreatedAt(), p.getUpdatedAt(),
				p.getSettledAt(), p.getFailedAt(), p.getReversedAt(), ledgerEntryIds);
	}

	private String newReference() {
		return UUID.randomUUID().toString();
	}

	private void assertCanView(PaymentTransaction p) {
		assertCanAccessPayer(p.getPayerAccount());
	}

	private void assertCanAccessPayer(Account payer) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) {
			throw new AccessDeniedException("Not authenticated");
		}
		boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
		if (admin) {
			return;
		}
		boolean merchant = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MERCHANT".equals(a.getAuthority()));
		if (!merchant) {
			throw new AccessDeniedException("Not allowed to access this payment");
		}
		String boundOwnerRef = ownerRefFrom(auth);
		if (boundOwnerRef == null || boundOwnerRef.isBlank() || !boundOwnerRef.equals(payer.getOwnerRef())) {
			throw new AccessDeniedException("Not allowed to access this payment");
		}
	}

	private String merchantScopeOwnerRefOrNull() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) {
			throw new AccessDeniedException("Not authenticated");
		}
		boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
		if (admin) {
			return null;
		}
		boolean merchant = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MERCHANT".equals(a.getAuthority()));
		if (!merchant) {
			throw new AccessDeniedException("Not allowed to list payments");
		}
		String bound = ownerRefFrom(auth);
		if (bound == null || bound.isBlank()) {
			throw new AccessDeniedException("Merchant owner not bound");
		}
		return bound;
	}

	private static String ownerRefFrom(Authentication auth) {
		if (auth.getDetails() instanceof JwtUserDetails(String ownerRef)) {
			return ownerRef;
		}
		return null;
	}
}
