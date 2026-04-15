package com.minipaygateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI miniPaymentGatewayOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Mini Payment Gateway API")
						.description("REST API per PRD v1.0 — use **Authentication** to obtain a JWT, then Authorize with `Bearer <token>`. "
								+ "Mutating POSTs under **/api/v1/accounts** and **/api/v1/payments** require header **X-Idempotency-Key** (UUID) when authenticated. "
								+ "Payment and reconcile mutations return **501** until Epics 5–6; RBAC (Epic 4) is enforced.")
						.version("0.0.1"))
				.components(new Components().addSecuritySchemes("bearer-jwt",
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("HS256 JWT from POST /api/v1/auth/token")));
	}
}
