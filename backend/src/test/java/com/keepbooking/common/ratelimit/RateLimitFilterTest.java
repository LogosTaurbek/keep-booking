package com.keepbooking.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keepbooking.common.config.AppProperties;

/**
 * Covers the parts that live outside RateLimitService: exempt-path bypass, auth-vs-general
 * bucket/limit selection by path prefix, X-Forwarded-For vs remoteAddr key derivation, and
 * the 429 response shape (status, Retry-After header, body).
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    private AppProperties appProperties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        filter = new RateLimitFilter(rateLimitService, appProperties, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void bypassesRateLimitingWhenDisabled() throws Exception {
        appProperties.getRateLimit().setEnabled(false);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/restaurants"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(rateLimitService, never()).tryConsume(any(), anyInt(), anyLong());
    }

    @Test
    void bypassesRateLimitingForExemptActuatorPath() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/actuator/health"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(rateLimitService, never()).tryConsume(any(), anyInt(), anyLong());
    }

    @Test
    void usesAuthLimitAndWindowForAuthPaths() throws Exception {
        when(rateLimitService.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/auth/login"), new MockHttpServletResponse(), chain);

        verify(rateLimitService).tryConsume(
                eq("ratelimit:auth:10.0.0.1"), eq(appProperties.getRateLimit().getAuthLimit()),
                eq(appProperties.getRateLimit().getAuthWindowMs()));
    }

    @Test
    void usesGeneralLimitAndWindowForNonAuthPaths() throws Exception {
        when(rateLimitService.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/restaurants"), new MockHttpServletResponse(), chain);

        verify(rateLimitService).tryConsume(
                eq("ratelimit:general:10.0.0.1"), eq(appProperties.getRateLimit().getGeneralLimit()),
                eq(appProperties.getRateLimit().getGeneralWindowMs()));
    }

    @Test
    void prefersXForwardedForOverRemoteAddrWhenPresent() throws Exception {
        when(rateLimitService.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        MockHttpServletRequest request = request("/api/v1/restaurants");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(rateLimitService).tryConsume(eq("ratelimit:general:203.0.113.5"), anyInt(), anyLong());
    }

    @Test
    void allowsRequestThroughWhenUnderLimit() throws Exception {
        when(rateLimitService.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/restaurants"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void respondsWithTooManyRequestsWhenOverLimit() throws Exception {
        when(rateLimitService.tryConsume(any(), anyInt(), anyLong())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/restaurants"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After"))
                .isEqualTo(String.valueOf(appProperties.getRateLimit().getGeneralWindowMs() / 1000));
        assertThat(response.getContentAsString()).contains("RATE_001");
    }
}
