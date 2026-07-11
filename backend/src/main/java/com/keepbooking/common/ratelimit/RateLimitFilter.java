package com.keepbooking.common.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepbooking.common.config.AppProperties;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.common.exception.ProblemDetail;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String[] EXEMPT_PATTERNS = {
            "/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/api-docs/**"
    };

    private final RateLimitService rateLimitService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        AppProperties.RateLimit config = appProperties.getRateLimit();
        if (!config.isEnabled() || isExempt(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isAuthPath = request.getRequestURI().startsWith("/api/v1/auth/");
        int limit = isAuthPath ? config.getAuthLimit() : config.getGeneralLimit();
        long windowMs = isAuthPath ? config.getAuthWindowMs() : config.getGeneralWindowMs();
        String bucket = isAuthPath ? "auth" : "general";
        String redisKey = "ratelimit:" + bucket + ":" + clientKey(request);

        if (rateLimitService.tryConsume(redisKey, limit, windowMs)) {
            filterChain.doFilter(request, response);
        } else {
            respondTooManyRequests(response, request, windowMs);
        }
    }

    private boolean isExempt(String path) {
        for (String pattern : EXEMPT_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void respondTooManyRequests(HttpServletResponse response, HttpServletRequest request, long windowMs)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(windowMs / 1000));
        ProblemDetail body = ProblemDetail.of(ErrorCode.RATE_LIMIT_EXCEEDED,
                ErrorCode.RATE_LIMIT_EXCEEDED.getDefaultMessage(), request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
