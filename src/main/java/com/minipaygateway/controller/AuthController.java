package com.minipaygateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.minipaygateway.config.SecurityUsersProperties;
import com.minipaygateway.dto.request.TokenRequest;
import com.minipaygateway.dto.response.TokenResponse;
import com.minipaygateway.security.JwtTokenProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Authentication")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final JwtTokenProvider jwtTokenProvider;
	private final SecurityUsersProperties securityUsersProperties;
	private final long expirySeconds;

	public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider,
			SecurityUsersProperties securityUsersProperties,
			@Value("${app.jwt.expiry-seconds}") long expirySeconds) {
		this.authenticationManager = authenticationManager;
		this.jwtTokenProvider = jwtTokenProvider;
		this.securityUsersProperties = securityUsersProperties;
		this.expirySeconds = expirySeconds;
	}

	@Operation(summary = "Issue JWT", description = "Returns a stateless JWT with role claims. Use dev users from application.yml (e.g. admin/admin).")
	@PostMapping("/api/v1/auth/token")
	public ResponseEntity<TokenResponse> token(@RequestBody @Valid TokenRequest request) {
		var auth = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
		var user = (UserDetails) auth.getPrincipal();
		var roles = user.getAuthorities().stream()
				.map(a -> a.getAuthority().replace("ROLE_", ""))
				.toList();
		String ownerRef = securityUsersProperties.getUsers().stream()
				.filter(u -> u.username().equals(user.getUsername()))
				.findFirst()
				.map(SecurityUsersProperties.UserEntry::ownerRef)
				.orElse(null);
		String jwt = jwtTokenProvider.createToken(user.getUsername(), roles, ownerRef);
		return ResponseEntity.ok(new TokenResponse(jwt, expirySeconds, roles));
	}
}
