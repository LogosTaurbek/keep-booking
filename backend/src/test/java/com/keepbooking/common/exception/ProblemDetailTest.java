package com.keepbooking.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * tz2.txt §5.1: RFC 7807 body must carry a stable "type" URI (for client-side error branching
 * without parsing "detail") and a "traceId" (for correlating a client-reported error with
 * server-side logs/traces).
 */
class ProblemDetailTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void ofDerivesAStableTypeUriFromTheErrorCode() {
        ProblemDetail pd = ProblemDetail.of(ErrorCode.TABLE_NOT_AVAILABLE, "detail", "/api/v1/bookings");

        assertThat(pd.getType()).isEqualTo("https://keepbooking.dev/errors/table-not-available");
        assertThat(pd.getCode()).isEqualTo("TABLE_002");
        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getDetail()).isEqualTo("detail");
        assertThat(pd.getInstance()).isEqualTo("/api/v1/bookings");
    }

    @Test
    void ofUsesTracingTraceIdWhenPresentInMdc() {
        MDC.put("traceId", "otel-trace-123");
        MDC.put("requestId", "req-456");

        ProblemDetail pd = ProblemDetail.of(ErrorCode.RESTAURANT_NOT_FOUND, "detail", "/api/v1/restaurants/1");

        assertThat(pd.getTraceId()).isEqualTo("otel-trace-123");
    }

    @Test
    void ofFallsBackToRequestIdWhenNoTracingTraceIdInMdc() {
        MDC.put("requestId", "req-456");

        ProblemDetail pd = ProblemDetail.of(ErrorCode.RESTAURANT_NOT_FOUND, "detail", "/api/v1/restaurants/1");

        assertThat(pd.getTraceId()).isEqualTo("req-456");
    }

    @Test
    void ofLeavesTraceIdNullWhenMdcIsEmpty() {
        ProblemDetail pd = ProblemDetail.of(ErrorCode.RESTAURANT_NOT_FOUND, "detail", "/api/v1/restaurants/1");

        assertThat(pd.getTraceId()).isNull();
    }
}
