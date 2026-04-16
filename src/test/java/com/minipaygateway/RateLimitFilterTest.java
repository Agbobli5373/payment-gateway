package com.minipaygateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipaygateway.config.RateLimitProperties;
import com.minipaygateway.filter.RateLimitFilter;

class RateLimitFilterTest {

	@Test
	void thirdPaymentRequestWithinMinute_returns429() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(new ObjectMapper(), new RateLimitProperties(2));

		MockHttpServletRequest req1 = request();
		MockHttpServletResponse res1 = new MockHttpServletResponse();
		filter.doFilter(req1, res1, new MockFilterChain());
		assertThat(res1.getStatus()).isEqualTo(200);

		MockHttpServletRequest req2 = request();
		MockHttpServletResponse res2 = new MockHttpServletResponse();
		filter.doFilter(req2, res2, new MockFilterChain());
		assertThat(res2.getStatus()).isEqualTo(200);

		MockHttpServletRequest req3 = request();
		MockHttpServletResponse res3 = new MockHttpServletResponse();
		filter.doFilter(req3, res3, new MockFilterChain());
		assertThat(res3.getStatus()).isEqualTo(429);
		assertThat(res3.getHeader("Retry-After")).isNotBlank();
		assertThat(res3.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
	}

	private static MockHttpServletRequest request() {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments/initiate");
		req.addHeader("X-Api-Key", "test-key");
		return req;
	}
}
