package com.localspot.exception;

/** Mã lỗi ổn định trả trong {@code ProblemDetail.code}. Frontend rẽ nhánh theo mã, không theo câu chữ. */
public final class ErrorCode {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";

    // Chung (GlobalExceptionHandler, chốt D7 2026-10-03)
    /** 400: request không đọc được — JSON hỏng, sai kiểu / thiếu tham số. */
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    /** 404 không có route; 404 của một bản ghi dùng mã riêng ({@code PLACE_NOT_FOUND}…). */
    public static final String NOT_FOUND = "NOT_FOUND";

    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String NOT_ACCEPTABLE = "NOT_ACCEPTABLE";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    /** 409: optimistic lock — bản ghi đã bị người khác sửa. */
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    /** 409: trùng khóa UNIQUE tới được CSDL (service không kịp chặn, vd. hai request chen nhau). */
    public static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";

    /** 429: vượt giới hạn tần suất (NFR-10) — kèm header {@code Retry-After}. */
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";

    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String REFRESH_TOKEN_INVALID = "REFRESH_TOKEN_INVALID";
    public static final String REFRESH_TOKEN_REUSED = "REFRESH_TOKEN_REUSED";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";
    public static final String INVALID_CURRENT_PASSWORD = "INVALID_CURRENT_PASSWORD";
    public static final String INVALID_CURSOR = "INVALID_CURSOR";

    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String PLACE_NOT_FOUND = "PLACE_NOT_FOUND";
    public static final String REVIEW_NOT_FOUND = "REVIEW_NOT_FOUND";
    public static final String COMMENT_NOT_FOUND = "COMMENT_NOT_FOUND";
    public static final String PHOTO_NOT_FOUND = "PHOTO_NOT_FOUND";
    /** 422 theo trường {@code photos[i]}: không phải JPEG / PNG, đuôi tệp không khớp, quá cỡ, ảnh hỏng (NFR-09). */
    public static final String INVALID_IMAGE = "INVALID_IMAGE";
    /** 422: quá số ảnh mỗi request (10) hoặc tổng ảnh của địa điểm (30 — chốt 2026-10-06). */
    public static final String PHOTO_LIMIT_EXCEEDED = "PHOTO_LIMIT_EXCEEDED";
    /** 409: quyết định trên địa điểm không còn PENDING — đã được duyệt / từ chối (chốt 2026-10-04). */
    public static final String PLACE_ALREADY_MODERATED = "PLACE_ALREADY_MODERATED";
    /** Admin tự khóa mình / tự gỡ ADMIN của mình (chốt 2026-10-03); kiểm duyệt viên tự duyệt đề xuất của mình (2026-10-04). */
    public static final String SELF_ACTION_FORBIDDEN = "SELF_ACTION_FORBIDDEN";
    /** Mọi tài khoản phải giữ role USER (chốt 2026-10-03). */
    public static final String ROLE_USER_REQUIRED = "ROLE_USER_REQUIRED";
    /** OWNER chỉ gán qua duyệt yêu cầu sở hữu (UC30), không gán / gỡ tay (chốt O6 2026-10-03). */
    public static final String OWNER_ROLE_MANAGED_BY_CLAIM = "OWNER_ROLE_MANAGED_BY_CLAIM";

    private ErrorCode() {}
}
