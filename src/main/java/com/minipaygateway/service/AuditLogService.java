package com.minipaygateway.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.minipaygateway.domain.AuditLog;
import com.minipaygateway.domain.enums.PaymentStatus;
import com.minipaygateway.repository.AuditLogRepository;

@Service
public class AuditLogService {

	private final AuditLogRepository auditLogRepository;

	public AuditLogService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void recordPaymentTransition(String paymentRef, PaymentStatus oldStatus, PaymentStatus newStatus) {
		AuditLog log = new AuditLog();
		log.setEntityId(paymentRef);
		log.setEntityType("PAYMENT");
		log.setOldStatus(oldStatus == null ? null : oldStatus.name());
		log.setNewStatus(newStatus == null ? null : newStatus.name());
		log.setActor(actor());
		log.setIpAddress(ipAddress());
		auditLogRepository.save(log);
	}

	private static String actor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
			return "system";
		}
		return auth.getName();
	}

	private static String ipAddress() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
			return attrs.getRequest().getRemoteAddr();
		}
		return null;
	}
}
