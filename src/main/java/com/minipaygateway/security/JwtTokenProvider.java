package com.minipaygateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final long expirySeconds;

	public JwtTokenProvider(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiry-seconds}") long expirySeconds) {
		this.key = hmacKeyFromSecret(secret);
		this.expirySeconds = expirySeconds;
	}

	private static SecretKey hmacKeyFromSecret(String secret) {
		byte[] material = secret.getBytes(StandardCharsets.UTF_8);
		if (material.length < 32) {
			try {
				material = MessageDigest.getInstance("SHA-256").digest(material);
			}
			catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
		}
		return Keys.hmacShaKeyFor(material);
	}

	public String createToken(String subject, List<String> roles) {
		var now = new Date();
		var exp = new Date(now.getTime() + expirySeconds * 1000);
		return Jwts.builder()
				.subject(subject)
				.claim("roles", roles)
				.issuedAt(now)
				.expiration(exp)
				.signWith(key)
				.compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
