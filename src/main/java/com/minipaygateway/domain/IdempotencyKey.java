package com.minipaygateway.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

	@Id
	@Column(name = "key", nullable = false)
	private UUID key;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "http_status", nullable = false)
	private short httpStatus;

	@Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
	private String responseBody;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public UUID getKey() {
		return key;
	}

	public void setKey(UUID key) {
		this.key = key;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public void setRequestHash(String requestHash) {
		this.requestHash = requestHash;
	}

	public short getHttpStatus() {
		return httpStatus;
	}

	public void setHttpStatus(short httpStatus) {
		this.httpStatus = httpStatus;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public void setResponseBody(String responseBody) {
		this.responseBody = responseBody;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
