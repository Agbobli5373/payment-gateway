package com.minipaygateway.filter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";

	public static final String TRACE_ID_ATTRIBUTE = "traceId";

	private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

	private final ObjectMapper objectMapper;

	public CorrelationIdFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String traceId = request.getHeader(TRACE_ID_HEADER);
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString();
		}
		request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
		response.setHeader(TRACE_ID_HEADER, traceId);
		Instant start = Instant.now();
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			logHttp(request, response, traceId, Duration.between(start, Instant.now()).toMillis());
		}
	}

	private void logHttp(HttpServletRequest request, HttpServletResponse response, String traceId, long durationMs) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("timestamp", Instant.now().toString());
		payload.put("traceId", traceId);
		payload.put("method", request.getMethod());
		payload.put("path", request.getRequestURI());
		payload.put("statusCode", response.getStatus());
		payload.put("durationMs", durationMs);
		payload.put("actor", actor());
		payload.put("remoteIp", request.getRemoteAddr());
		try {
			log.info(objectMapper.writeValueAsString(payload));
		}
		catch (JsonProcessingException ex) {
			log.info("{\"traceId\":\"{}\",\"method\":\"{}\",\"path\":\"{}\",\"statusCode\":{}}",
					traceId, request.getMethod(), request.getRequestURI(), response.getStatus());
		}
	}

	private static String actor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
			return "anonymous";
		}
		return auth.getName();
	}
}
