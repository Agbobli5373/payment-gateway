package com.minipaygateway.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.minipaygateway.security.JwtTokenProvider;
import com.minipaygateway.security.JwtUserDetails;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/actuator")
				|| path.startsWith("/v3/api-docs")
				|| path.startsWith("/swagger-ui")
				|| "/swagger-ui.html".equals(path)
				|| "POST".equalsIgnoreCase(request.getMethod()) && "/api/v1/auth/token".equals(path);
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}
		String token = header.substring(7);
		try {
			var claims = jwtTokenProvider.parseClaims(token);
			String username = claims.getSubject();
			Object rawRoles = claims.get("roles");
			var authorities = parseRoleAuthorities(rawRoles);
			if (rawRoles instanceof List<?> rl && !rl.isEmpty() && authorities.isEmpty()) {
				SecurityContextHolder.clearContext();
				filterChain.doFilter(request, response);
				return;
			}
			String ownerRef = claims.get("ownerRef", String.class);
			if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
				auth.setDetails(new JwtUserDetails(ownerRef));
				SecurityContextHolder.getContext().setAuthentication(auth);
			}
		}
		catch (JwtException | IllegalArgumentException ignored) {
			SecurityContextHolder.clearContext();
		}
		filterChain.doFilter(request, response);
	}

	public static List<SimpleGrantedAuthority> parseRoleAuthorities(Object raw) {
		if (raw == null) {
			return List.of();
		}
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		var out = new ArrayList<SimpleGrantedAuthority>();
		for (Object o : list) {
			if (o instanceof String s && !s.isBlank()) {
				out.add(new SimpleGrantedAuthority("ROLE_" + s));
			}
		}
		return List.copyOf(out);
	}
}
