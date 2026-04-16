package com.minipaygateway.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.config.RateLimitProperties;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	private final ObjectMapper objectMapper;

	private final RateLimitProperties rateLimitProperties;

	public RateLimitFilter(ObjectMapper objectMapper, RateLimitProperties rateLimitProperties) {
		this.objectMapper = objectMapper;
		this.rateLimitProperties = rateLimitProperties;
	}

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/v1/payments");
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String key = rateLimitKey(request);
		Bucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			long retryAfterSeconds = Math.max(1L, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
			var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
					"Rate limit exceeded. Retry later.");
			problem.setTitle("Too Many Requests");
			problem.setProperty("code", "RATE_LIMIT_EXCEEDED");
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			objectMapper.writeValue(response.getOutputStream(), problem);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private Bucket newBucket() {
		int limit = Math.max(1, rateLimitProperties.requestsPerMinute());
		Bandwidth bandwidth = Bandwidth.builder()
				.capacity(limit)
				.refillIntervally(limit, Duration.ofMinutes(1))
				.build();
		return Bucket.builder().addLimit(bandwidth).build();
	}

	private static String rateLimitKey(HttpServletRequest request) {
		String apiKey = request.getHeader("X-Api-Key");
		if (apiKey != null && !apiKey.isBlank()) {
			return "api:" + apiKey;
		}
		String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (auth != null && auth.startsWith("Bearer ")) {
			return "bearer:" + auth.substring(7);
		}
		return "ip:" + request.getRemoteAddr();
	}
}
