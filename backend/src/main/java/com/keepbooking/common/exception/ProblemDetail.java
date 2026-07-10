package com.keepbooking.common.exception;

import java.time.Instant;
import java.util.List;

import lombok.Data;

@Data
public class ProblemDetail {

    private String code;
    private int status;
    private String title;
    private String detail;
    private String instance;
    private Instant timestamp;
    private List<FieldViolation> errors;

    public static ProblemDetail of(ErrorCode errorCode, String detail, String instance) {
        ProblemDetail pd = new ProblemDetail();
        pd.code = errorCode.getCode();
        pd.status = errorCode.getHttpStatus().value();
        pd.title = errorCode.getHttpStatus().getReasonPhrase();
        pd.detail = detail;
        pd.instance = instance;
        pd.timestamp = Instant.now();
        return pd;
    }

    public record FieldViolation(String field, String message) {}
}
