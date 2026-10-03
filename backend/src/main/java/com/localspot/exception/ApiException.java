package com.localspot.exception;

import java.io.Serializable;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ có chủ đích, được {@link GlobalExceptionHandler} chuyển thành RFC 7807 {@code ProblemDetail}.
 * {@code code} là mã ổn định cho frontend (schema {@code Problem.code} trong openapi), {@code detail} là câu tiếng Việt
 * hiển thị được. {@code errors} (có thể rỗng) gắn lỗi vào trường của form — cùng dạng {@code errors[]} với lỗi validate.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;
    private final List<FieldError> errors;

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, List.of());
    }

    public ApiException(HttpStatus status, String code, String detail, List<FieldError> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    /** 422 gắn vào một trường — form hiển thị lỗi ngay dưới ô nhập. */
    public static ApiException fieldError(String code, String field, String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, code, message, List.of(new FieldError(field, message)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public record FieldError(String field, String message) implements Serializable {}
}
