package com.minipaygateway.filter;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Require a valid UUID {@link #HEADER} on mutating POSTs under /api/v1/accounts and /api/v1/payments.
 * Replay and conflict handling for accounts are in {@link com.minipaygateway.service.IdempotencyService}.
 */
public class IdempotencyKeyHeaderFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Idempotency-Key";

	private final ObjectMapper objectMapper;

	public IdempotencyKeyHeaderFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		if ("POST".equalsIgnoreCase(request.getMethod()) && requiresIdempotencyKey(request.getRequestURI())) {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
				filterChain.doFilter(request, response);
				return;
			}
			String key = request.getHeader(HEADER);
			if (key == null || key.isBlank()) {
				writeMissingKey(response);
				return;
			}
			try {
				UUID.fromString(key.trim());
			}
			catch (IllegalArgumentException e) {
				writeMissingKey(response);
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	static boolean requiresIdempotencyKey(String requestUri) {
		if (requestUri == null) {
			return false;
		}
		if ("/api/v1/accounts".equals(requestUri)) {
			return true;
		}
		return requestUri.equals("/api/v1/payments") || requestUri.startsWith("/api/v1/payments/");
	}

	private void writeMissingKey(HttpServletResponse response) throws IOException {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Header " + HEADER + " is required and must be a UUID");
		problem.setTitle("Bad Request");
		problem.setProperty("code", "MISSING_IDEMPOTENCY_KEY");
		response.setStatus(HttpStatus.BAD_REQUEST.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), problem);
	}
}
