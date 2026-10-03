package com.localspot.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thay {@code BasicErrorController} của Spring Boot ở {@code /error} — nơi servlet container chuyển tới khi lỗi phát
 * sinh <b>ngoài</b> Spring MVC, nên {@link GlobalExceptionHandler} không bắt được. Ví dụ: CSDL sập đúng lúc filter JWT
 * nạp quyền người dùng. Không có lớp này, client nhận JSON dạng khác ({@code timestamp, status, error, path}) không có
 * {@code code} / {@code errorId}.
 *
 * <p>{@code /error} đã nằm trong danh sách {@code permitAll} của {@code SecurityConfig}.
 */
@RestController
public class FallbackErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(FallbackErrorController.class);

    /** Không khai báo {@code produces}: trang lỗi phải trả được cho mọi {@code Accept}, kể cả trình duyệt. */
    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = statusOf(request);
        ProblemDetail body = Problems.forStatus(status);
        if (status.is5xxServerError()) {
            String errorId = Problems.newErrorId();
            Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            Throwable ex = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable t ? t : null;
            log.error("Lỗi ngoài Spring MVC errorId={} tại {}", errorId, uri, ex);
            body.setProperty("errorId", errorId);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static HttpStatus statusOf(HttpServletRequest request) {
        if (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null && status.isError()) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
