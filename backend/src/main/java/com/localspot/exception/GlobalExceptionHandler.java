package com.localspot.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
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
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Chuẩn hóa mọi lỗi thành RFC 7807 {@code ProblemDetail} kèm {@code code} ổn định (openapi schema {@code Problem}) —
 * frontend rẽ nhánh theo {@code code}, không theo câu chữ. Lỗi của Spring Security (401/403 phát sinh trong filter) cũng
 * được đưa về đây qua {@link com.localspot.security.ProblemDetailsSecurityHandler} để chỉ có một định dạng lỗi.
 *
 * <p>Response thành công không bọc envelope: trả thẳng DTO như openapi (HTTP status đã mang thông tin thành công).
 *
 * <p>Phân loại (chốt 2026-10-03):
 *
 * <ul>
 *   <li><b>400 {@code MALFORMED_REQUEST}</b>: request không đọc được — JSON hỏng, sai kiểu tham số, thiếu tham số bắt
 *       buộc. Frontend gửi đúng kiểu thì không bao giờ gặp → 400 là lỗi lập trình phía client.
 *   <li><b>422 {@code VALIDATION_FAILED}</b>: đọc được nhưng sai luật — kèm {@code errors[]} theo trường.
 *   <li><b>409</b>: xung đột trạng thái — optimistic lock ({@code CONCURRENT_MODIFICATION}), trùng khóa UNIQUE
 *       ({@code DUPLICATE_RESOURCE}).
 *   <li><b>500 {@code INTERNAL_ERROR}</b>: câu chung chung, không lộ stack trace / SQL; kèm {@code errorId} ngẫu nhiên,
 *       log ghi cùng {@code errorId} + stack trace để tra đúng dòng.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Mã lỗi MySQL (vendor code) — phân loại vi phạm ràng buộc CSDL. */
    private static final int MYSQL_DUPLICATE_KEY = 1062;

    private static final int MYSQL_FOREIGN_KEY_MISSING_PARENT = 1452;
    private static final int MYSQL_CHECK_VIOLATED = 3819;

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail body = Problems.of(ex.getStatus(), ex.getCode(), ex.getMessage());
        if (!ex.getErrors().isEmpty()) {
            body.setProperty("errors", ex.getErrors());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /** 429 kèm {@code Retry-After} (giây) — openapi {@code TooManyRequests}. */
    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(Problems.of(ex.getStatus(), ex.getCode(), ex.getMessage()));
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

    /**
     * Hai người sửa cùng một bản ghi (cột {@code version}): người lưu sau nhận 409 thay vì ghi đè âm thầm. Bắt cả
     * ngoại lệ JPA gốc phòng khi lỗi phát sinh ngoài repository (không qua lớp dịch ngoại lệ của Spring).
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> handleOptimisticLock(Exception ex) {
        log.info("Xung đột optimistic lock: {}", ex.getMessage());
        return problem(
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION,
                "Dữ liệu vừa được người khác cập nhật. Hãy tải lại rồi thử lại.");
    }

    /**
     * Vi phạm ràng buộc CSDL — lớp phòng thủ cuối (NFR-12). Service nên kiểm tra trước và ném {@link ApiException} có mã
     * cụ thể (vd. {@code EMAIL_ALREADY_EXISTS}); tới được đây nghĩa là hai request chen nhau hoặc thiếu validate ở tầng
     * ứng dụng → log WARN để sửa. Phân loại theo mã lỗi MySQL vì Spring dịch không đồng nhất: CHECK (3819) có SQLSTATE
     * HY000 nên thành {@code UncategorizedSQLException} chứ không phải {@code DataIntegrityViolationException} (phát
     * hiện ở D1). Lỗi CSDL khác (mất kết nối, FK 1451 khi xóa cứng — lỗi lập trình…) → 500.
     */
    @ExceptionHandler({DataAccessException.class, PersistenceException.class})
    ResponseEntity<ProblemDetail> handleDataAccess(Exception ex, HttpServletRequest request) {
        Integer vendorCode = mysqlErrorCode(ex);
        if (vendorCode == null) {
            return internalError(ex, request);
        }
        return switch (vendorCode) {
            case MYSQL_DUPLICATE_KEY -> {
                log.warn("Trùng khóa UNIQUE tới được CSDL tại {} {}", request.getMethod(), request.getRequestURI());
                yield problem(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, "Dữ liệu này đã tồn tại.");
            }
            case MYSQL_CHECK_VIOLATED, MYSQL_FOREIGN_KEY_MISSING_PARENT -> {
                log.warn(
                        "Ràng buộc CSDL {} chặn dữ liệu sai tại {} {} — thiếu validate tầng ứng dụng",
                        vendorCode,
                        request.getMethod(),
                        request.getRequestURI());
                yield problem(
                        HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.VALIDATION_FAILED, "Dữ liệu gửi lên không hợp lệ.");
            }
            default -> internalError(ex, request);
        };
    }

    /** Lưới cuối: mọi lỗi chưa được phân loại → 500 an toàn + {@code errorId}. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        return internalError(ex, request);
    }

    /** Lỗi validate DTO: 422 (openapi {@code ValidationError}) thay vì 400 mặc định, kèm lỗi từng trường. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiException.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiException.FieldError(e.getField(), messageOf(e)))
                .toList();
        return ResponseEntity.unprocessableContent().body(validationProblem(errors));
    }

    /**
     * Ràng buộc đặt thẳng trên tham số controller ({@code @Min} trên {@code @RequestParam}...) — cùng định dạng 422
     * như lỗi validate DTO, thay vì 400 mặc định của Spring.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiException.FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new ApiException.FieldError(name == null ? "" : name, messageOf(error)));
            }
        }
        return ResponseEntity.unprocessableContent().body(validationProblem(errors));
    }

    /**
     * Mọi ngoại lệ Spring MVC mà lớp cha xử lý (JSON hỏng, sai kiểu tham số, 404 không có route, 405, 415…) đi qua đây:
     * gắn {@code code} theo HTTP status và câu tiếng Việt, để không lỗi nào thiếu {@code code}. Lỗi 5xx của nhóm này
     * (vd. không ghi được response) được log kèm {@code errorId} như lỗi 500 khác.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ProblemDetail problem = Problems.forStatus(status);
        if (status.is5xxServerError()) {
            String errorId = Problems.newErrorId();
            log.error("Lỗi máy chủ errorId={} ({})", errorId, request.getDescription(false), ex);
            problem.setProperty("errorId", errorId);
        }
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    private static ResponseEntity<ProblemDetail> internalError(Exception ex, HttpServletRequest request) {
        String errorId = Problems.newErrorId();
        // Chỉ ghi method + path (không query string) — query có thể chứa dữ liệu cá nhân (NFR-11)
        log.error("Lỗi không mong đợi errorId={} tại {} {}", errorId, request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail body = Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Đã có lỗi xảy ra. Hãy thử lại sau; nếu vẫn lỗi, gửi mã " + errorId + " cho quản trị viên.");
        body.setProperty("errorId", errorId);
        return ResponseEntity.internalServerError().body(body);
    }

    /** Mã lỗi MySQL đầu tiên tìm thấy trong chuỗi nguyên nhân, hoặc {@code null}. */
    private static Integer mysqlErrorCode(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() != 0) {
                return sql.getErrorCode();
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    private static ProblemDetail validationProblem(List<ApiException.FieldError> errors) {
        ProblemDetail body = Problems.of(
                HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.VALIDATION_FAILED, "Dữ liệu gửi lên không hợp lệ.");
        body.setProperty("errors", errors);
        return body;
    }

    private static String messageOf(MessageSourceResolvable error) {
        return error.getDefaultMessage() == null ? "" : error.getDefaultMessage();
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(Problems.of(status, code, detail));
    }
}
