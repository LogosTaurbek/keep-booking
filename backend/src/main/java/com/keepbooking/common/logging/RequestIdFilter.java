package com.keepbooking.common.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Puts a correlation ID in MDC (and echoes it back in the response) so structured
 * log lines from the same request can be grepped/joined in log aggregation tools.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    // Client-supplied values are echoed straight back into a response header - unrestricted, that's
    // a CRLF/header-injection vector (OWASP), so only accept a safe correlation-id-shaped value and
    // fall back to a fresh UUID for anything else, same as if no header were sent at all.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String suppliedRequestId = request.getHeader(HEADER);
        String requestId = (StringUtils.hasText(suppliedRequestId) && SAFE_REQUEST_ID.matcher(suppliedRequestId).matches())
                ? suppliedRequestId
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
