package com.minipaygateway.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.minipaygateway.domain.PaymentTransaction;

import jakarta.persistence.LockModeType;

public interface PaymentTransactionRepository
		extends JpaRepository<PaymentTransaction, Long>, JpaSpecificationExecutor<PaymentTransaction> {

	boolean existsByReference(String reference);

	Optional<PaymentTransaction> findByReference(String reference);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PaymentTransaction p join fetch p.payerAccount join fetch p.payeeAccount where p.reference = :ref")
	Optional<PaymentTransaction> findByReferenceForUpdate(@Param("ref") String reference);
}
