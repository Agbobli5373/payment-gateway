package com.minipaygateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
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
								+ "Reconciliation endpoints are available under **/api/v1/reconcile** (ADMIN, AUDITOR). RBAC (Epic 4) is enforced.")
						.version("0.0.1"))
				.components(new Components()
						.addSecuritySchemes("bearer-jwt",
								new SecurityScheme()
										.type(SecurityScheme.Type.HTTP)
										.scheme("bearer")
										.bearerFormat("JWT")
										.description("HS256 JWT from POST /api/v1/auth/token"))
						.addSchemas("ProblemDetail", new ObjectSchema()
								.addProperty("type", new StringSchema().example("about:blank"))
								.addProperty("title", new StringSchema().example("Bad Request"))
								.addProperty("status", new IntegerSchema().example(400))
								.addProperty("detail", new StringSchema().example("Request validation failed"))
								.addProperty("instance", new StringSchema().example("/api/v1/payments/initiate"))
								.addProperty("code", new StringSchema().example("VALIDATION_ERROR")))
						.addResponses("UnauthorizedError",
								new ApiResponse().description("Authentication required (code=UNAUTHORIZED)"))
						.addResponses("ForbiddenError",
								new ApiResponse().description("Insufficient permissions (code=FORBIDDEN)"))
						.addResponses("ValidationError",
								new ApiResponse().description("Validation or malformed request (code=VALIDATION_ERROR)"))
						.addResponses("NotFoundError",
								new ApiResponse().description("Resource not found"))
						.addResponses("ConflictError",
								new ApiResponse().description("Conflict (e.g. idempotency conflict)"))
						.addResponses("UnprocessableError",
								new ApiResponse().description("Domain rule violation"))
						.addResponses("RateLimitError",
								new ApiResponse().description("Rate limit exceeded (code=RATE_LIMIT_EXCEEDED)"))
						.addResponses("ServiceUnavailableError",
								new ApiResponse().description("Database temporarily unavailable (code=SERVICE_UNAVAILABLE)")));
	}
}
