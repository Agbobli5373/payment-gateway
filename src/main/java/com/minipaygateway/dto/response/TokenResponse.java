package com.minipaygateway.dto.response;

import java.util.List;

public record TokenResponse(String token, long expiresIn, List<String> roles) {
}
