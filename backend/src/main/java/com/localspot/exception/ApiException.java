package com.localspot.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ có chủ đích, được {@link GlobalExceptionHandler} chuyển thành RFC 7807 {@code ProblemDetail}.
 * {@code code} là mã ổn định cho frontend (schema {@code Problem.code} trong openapi), {@code detail} là câu tiếng Việt
 * hiển thị được.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
