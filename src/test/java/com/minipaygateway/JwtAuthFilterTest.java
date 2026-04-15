package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.minipaygateway.filter.JwtAuthFilter;
import com.minipaygateway.security.JwtTokenProvider;
import com.minipaygateway.security.JwtUserDetails;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void parseRoleAuthorities_skipsNonStringElements() {
		var authorities = JwtAuthFilter.parseRoleAuthorities(List.of("MERCHANT", 42, "", "ADMIN"));
		assertThat(authorities).containsExactly(
				new SimpleGrantedAuthority("ROLE_MERCHANT"),
				new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	@Test
	void parseRoleAuthorities_nonList_returnsEmpty() {
		assertThat(JwtAuthFilter.parseRoleAuthorities("MERCHANT")).isEmpty();
	}

	@Test
	void doFilter_malformedRolesClaim_doesNotThrowAndLeavesContextCleared() throws Exception {
		JwtTokenProvider provider = mock(JwtTokenProvider.class);
		Claims claims = mock(Claims.class);
		when(claims.getSubject()).thenReturn("merchant");
		when(claims.get("roles")).thenReturn(List.of(1, 2, 3));
		when(provider.parseClaims(anyString())).thenReturn(claims);

		JwtAuthFilter filter = new JwtAuthFilter(provider);
		var request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer fake.jwt.token");
		var response = new MockHttpServletResponse();
		var chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void doFilter_validStringRoles_setsAuthenticationWithJwtUserDetails() throws Exception {
		JwtTokenProvider provider = mock(JwtTokenProvider.class);
		Claims claims = mock(Claims.class);
		when(claims.getSubject()).thenReturn("merchant");
		when(claims.get("roles")).thenReturn(List.of("MERCHANT"));
		when(claims.get("ownerRef", String.class)).thenReturn("merchant-acme");
		when(provider.parseClaims(anyString())).thenReturn(claims);

		JwtAuthFilter filter = new JwtAuthFilter(provider);
		var request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer fake.jwt.token");
		var response = new MockHttpServletResponse();
		var chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		var auth = SecurityContextHolder.getContext().getAuthentication();
		assertThat(auth).isNotNull();
		assertThat(auth.getName()).isEqualTo("merchant");
		assertThat(auth.getDetails()).isInstanceOf(JwtUserDetails.class);
		assertThat(((JwtUserDetails) auth.getDetails()).ownerRef()).isEqualTo("merchant-acme");
	}
}
