package com.localspot.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Dựng {@code ProblemDetail} dùng chung cho {@link GlobalExceptionHandler} (lỗi trong Spring MVC) và
 * {@link FallbackErrorController} (lỗi ngoài Spring MVC) — một định dạng lỗi duy nhất dù lỗi phát sinh ở đâu.
 */
final class Problems {

    private Problems() {}

    static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return body;
    }

    /** Lỗi chỉ biết HTTP status (lỗi Spring MVC, lỗi container): {@code code} + câu tiếng Việt theo status. */
    static ProblemDetail forStatus(HttpStatus status) {
        return of(status, codeFor(status), detailFor(status));
    }

    /** Mã tra cứu gắn vào response 5xx và dòng log tương ứng. */
    static String newErrorId() {
        return UUID.randomUUID().toString();
    }

    static String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.MALFORMED_REQUEST;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> ErrorCode.NOT_ACCEPTABLE;
            case CONTENT_TOO_LARGE -> ErrorCode.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case UNPROCESSABLE_CONTENT -> ErrorCode.VALIDATION_FAILED;
            case TOO_MANY_REQUESTS -> ErrorCode.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : "HTTP_" + status.value();
        };
    }

    static String detailFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Yêu cầu không đúng định dạng.";
            case UNAUTHORIZED -> "Chưa đăng nhập hoặc phiên đã hết hạn.";
            case FORBIDDEN -> "Bạn không có quyền thực hiện thao tác này.";
            case NOT_FOUND -> "Không tìm thấy tài nguyên.";
            case METHOD_NOT_ALLOWED -> "Phương thức không được hỗ trợ cho đường dẫn này.";
            case NOT_ACCEPTABLE -> "Không có định dạng phản hồi phù hợp.";
            case CONTENT_TOO_LARGE -> "Dữ liệu gửi lên quá lớn.";
            case UNSUPPORTED_MEDIA_TYPE -> "Định dạng dữ liệu gửi lên không được hỗ trợ.";
            case UNPROCESSABLE_CONTENT -> "Dữ liệu gửi lên không hợp lệ.";
            case TOO_MANY_REQUESTS -> "Bạn thao tác quá nhiều lần. Hãy thử lại sau.";
            case SERVICE_UNAVAILABLE -> "Dịch vụ tạm thời không khả dụng. Hãy thử lại sau.";
            default -> status.is5xxServerError() ? "Đã có lỗi xảy ra. Hãy thử lại sau." : status.getReasonPhrase();
        };
    }
}
