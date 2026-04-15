package com.minipaygateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityUsersProperties {

	private final List<UserEntry> users = new ArrayList<>();

	public List<UserEntry> getUsers() {
		return users;
	}

	/**
	 * @param ownerRef optional; when set for a MERCHANT user, included in JWT and used to authorize balance reads.
	 */
	public record UserEntry(String username, String password, List<String> roles, String ownerRef) {
		public UserEntry {
			roles = roles == null ? List.of() : List.copyOf(roles);
		}
	}
}
