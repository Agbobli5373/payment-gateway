package com.minipaygateway.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "entity_id", nullable = false, length = 64, updatable = false)
	private String entityId;

	@Column(name = "entity_type", nullable = false, length = 32, updatable = false)
	private String entityType;

	@Column(name = "old_status", length = 32, updatable = false)
	private String oldStatus;

	@Column(name = "new_status", length = 32, updatable = false)
	private String newStatus;

	@Column(nullable = false, length = 255, updatable = false)
	private String actor;

	@Column(name = "ip_address", length = 45, updatable = false)
	private String ipAddress;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public void setOldStatus(String oldStatus) {
		this.oldStatus = oldStatus;
	}

	public void setNewStatus(String newStatus) {
		this.newStatus = newStatus;
	}

	public void setActor(String actor) {
		this.actor = actor;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
}
