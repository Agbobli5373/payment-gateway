package com.minipaygateway.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.minipaygateway.domain.PaymentTransaction;
import com.minipaygateway.domain.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentTransactionRepository
		extends JpaRepository<PaymentTransaction, Long>, JpaSpecificationExecutor<PaymentTransaction> {

	boolean existsByReference(String reference);

	Optional<PaymentTransaction> findByReference(String reference);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PaymentTransaction p join fetch p.payerAccount join fetch p.payeeAccount where p.reference = :ref")
	Optional<PaymentTransaction> findByReferenceForUpdate(@Param("ref") String reference);

	@Query("""
			select p from PaymentTransaction p
			where p.status = :settled
			  and upper(p.currency) = upper(:currency)
			  and p.settledAt is not null
			  and p.settledAt >= :fromInclusive
			  and p.settledAt <= :toInclusive
			""")
	List<PaymentTransaction> findSettledForReconciliation(@Param("settled") PaymentStatus settled,
			@Param("currency") String currency, @Param("fromInclusive") Instant fromInclusive,
			@Param("toInclusive") Instant toInclusive);
}
