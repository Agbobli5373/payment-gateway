package com.minipaygateway.security;

/**
 * Carries optional merchant scope from JWT into {@link org.springframework.security.core.Authentication#getDetails()}.
 */
public record JwtUserDetails(String ownerRef) {
}
