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

	public record UserEntry(String username, String password, List<String> roles) {
	}
}
