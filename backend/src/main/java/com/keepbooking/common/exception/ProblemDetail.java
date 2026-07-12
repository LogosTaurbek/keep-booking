package com.keepbooking.common.exception;

import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;

import lombok.Data;

/**
 * RFC 7807 Problem Details, plus a stable {@code code} for programmatic handling and a
 * {@code traceId} for correlating a client-reported error with server-side logs/traces.
 */
@Data
public class ProblemDetail {

    private String type;
    private String code;
    private int status;
    private String title;
    private String detail;
    private String instance;
    private String traceId;
    private Instant timestamp;
    private List<FieldViolation> errors;

    public static ProblemDetail of(ErrorCode errorCode, String detail, String instance) {
        ProblemDetail pd = new ProblemDetail();
        pd.type = errorCode.getTypeUri();
        pd.code = errorCode.getCode();
        pd.status = errorCode.getHttpStatus().value();
        pd.title = errorCode.getHttpStatus().getReasonPhrase();
        pd.detail = detail;
        pd.instance = instance;
        pd.traceId = resolveTraceId();
        pd.timestamp = Instant.now();
        return pd;
    }

    private static String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : MDC.get("requestId");
    }

    public record FieldViolation(String field, String message) {}
}
