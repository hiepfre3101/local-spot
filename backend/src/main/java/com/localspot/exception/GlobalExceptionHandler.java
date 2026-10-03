package com.localspot.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Chuẩn hóa mọi lỗi thành RFC 7807 {@code ProblemDetail} kèm {@code code} ổn định (openapi schema {@code Problem}).
 * Lỗi của Spring Security (401/403 phát sinh trong filter) cũng được đưa về đây qua
 * {@link com.localspot.security.ProblemDetailsSecurityHandler} để chỉ có một định dạng lỗi.
 *
 * <p>Bản D3 chỉ gồm các lỗi luồng xác thực cần; D7 bổ sung 404, optimistic lock, vi phạm ràng buộc CSDL (gồm mã 3819
 * của CHECK), 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail body = problemBody(ex.getStatus(), ex.getCode(), ex.getMessage());
        if (!ex.getErrors().isEmpty()) {
            body.setProperty("errors", ex.getErrors());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        ResponseEntity<ProblemDetail> response = ex instanceof LockedException
                ? problem(HttpStatus.UNAUTHORIZED, ErrorCode.ACCOUNT_LOCKED, "Tài khoản đang bị khóa.")
                : problem(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Chưa đăng nhập hoặc phiên đã hết hạn.");
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(response.getBody());
    }

    /**
     * Khách chưa đăng nhập chạm vào tài nguyên cần quyền phải nhận 401 (để frontend chuyển tới đăng nhập), không phải
     * 403 — advice này bắt cả AccessDeniedException ném từ {@code @PreAuthorize} trước khi filter của Security kịp phân
     * biệt.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return handleAuthentication(new InsufficientAuthenticationException(ex.getMessage(), ex));
        }
        return problem(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
    }

    /** Lỗi validate DTO: 422 (openapi {@code ValidationError}) thay vì 400 mặc định, kèm lỗi từng trường. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of(
                        "field", e.getField(), "message", e.getDefaultMessage() == null ? "" : e.getDefaultMessage()))
                .toList();
        ProblemDetail body =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Dữ liệu gửi lên không hợp lệ.");
        body.setProperty("code", ErrorCode.VALIDATION_FAILED);
        body.setProperty("errors", errors);
        return ResponseEntity.unprocessableContent().body(body);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(problemBody(status, code, detail));
    }

    private static ProblemDetail problemBody(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return body;
    }
}
