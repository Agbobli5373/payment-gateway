package com.minipaygateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.minipaygateway.dto.request.TokenRequest;
import com.minipaygateway.dto.response.TokenResponse;
import com.minipaygateway.security.JwtTokenProvider;

import jakarta.validation.Valid;

@RestController
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final JwtTokenProvider jwtTokenProvider;
	private final long expirySeconds;

	public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider,
			@Value("${app.jwt.expiry-seconds}") long expirySeconds) {
		this.authenticationManager = authenticationManager;
		this.jwtTokenProvider = jwtTokenProvider;
		this.expirySeconds = expirySeconds;
	}

	@PostMapping("/api/v1/auth/token")
	public ResponseEntity<TokenResponse> token(@RequestBody @Valid TokenRequest request) {
		var auth = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
		var user = (UserDetails) auth.getPrincipal();
		var roles = user.getAuthorities().stream()
				.map(a -> a.getAuthority().replace("ROLE_", ""))
				.toList();
		String jwt = jwtTokenProvider.createToken(user.getUsername(), roles);
		return ResponseEntity.ok(new TokenResponse(jwt, expirySeconds, roles));
	}
}
