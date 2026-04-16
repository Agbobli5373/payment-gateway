package com.minipaygateway.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.minipaygateway.domain.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	@Query(value = """
			select coalesce(sum(case when credit_account_id = :accountId then amount else 0 end), 0)
			     - coalesce(sum(case when debit_account_id = :accountId then amount else 0 end), 0)
			from ledger_entries
			where (credit_account_id = :accountId or debit_account_id = :accountId)
			  and currency = :currency
			""", nativeQuery = true)
	BigDecimal computeNetBalance(@Param("accountId") long accountId, @Param("currency") String currency);

	List<LedgerEntry> findByPaymentTransactionIdOrderByIdAsc(Long paymentTransactionId);

	List<LedgerEntry> findByPaymentTransactionIdIn(Collection<Long> paymentTransactionIds);

	@Query(value = """
			select * from ledger_entries e
			where upper(e.currency) = upper(:currency)
			  and e.created_at >= :fromInclusive
			  and e.created_at <= :toInclusive
			  and e.reference in ('PROCESS', 'SETTLE', 'REVERSE')
			  and (
			    e.transaction_id is null
			    or not exists (select 1 from payment_transactions p where p.id = e.transaction_id)
			    or (
			      exists (select 1 from payment_transactions p where p.id = e.transaction_id)
			      and not exists (
			        select 1 from payment_transactions p
			        where p.id = e.transaction_id
			          and p.status = 'SETTLED'
			          and upper(p.currency) = upper(:currency)
			          and p.settled_at is not null
			          and p.settled_at >= :settledFromInclusive
			          and p.settled_at <= :settledToInclusive
			      )
			      and not exists (
			        select 1 from payment_transactions p
			        where p.id = e.transaction_id
			          and p.status in ('REVERSED', 'FAILED')
			      )
			    )
			  )
			""", nativeQuery = true)
	List<LedgerEntry> findOrphanedPaymentLedgerInWindow(@Param("currency") String currency,
			@Param("fromInclusive") Instant fromInclusive, @Param("toInclusive") Instant toInclusive,
			@Param("settledFromInclusive") Instant settledFromInclusive,
			@Param("settledToInclusive") Instant settledToInclusive);
}
