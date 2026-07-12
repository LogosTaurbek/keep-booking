package com.keepbooking.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

/**
 * A client-supplied X-Request-Id is echoed back into a response header - unvalidated, that's a
 * CRLF/header-injection vector (OWASP Top-10, tz2.txt §23), so only a safe-shaped value may pass
 * through as-is.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void echoesBackAWellFormedClientSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "client-supplied-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("client-supplied-id-123");
        verify(chain).doFilter(request, response);
    }

    @Test
    void generatesAFreshIdWhenNoHeaderIsSupplied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void replacesAMalformedOrPotentiallyMaliciousRequestIdWithAFreshUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "abc\r\nSet-Cookie: evil=1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id"))
                .isNotEqualTo("abc\r\nSet-Cookie: evil=1")
                .matches("[A-Za-z0-9-]{36}");
    }
}
