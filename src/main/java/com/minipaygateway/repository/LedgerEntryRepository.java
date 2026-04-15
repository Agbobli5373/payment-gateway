package com.minipaygateway.repository;

import java.math.BigDecimal;

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
}
