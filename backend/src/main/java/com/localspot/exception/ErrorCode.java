package com.localspot.exception;

/** Mã lỗi ổn định trả trong {@code ProblemDetail.code}. Frontend rẽ nhánh theo mã, không theo câu chữ. */
public final class ErrorCode {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";

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
    /** Admin tự khóa mình / tự gỡ ADMIN của mình (chốt 2026-10-03). */
    public static final String SELF_ACTION_FORBIDDEN = "SELF_ACTION_FORBIDDEN";
    /** Mọi tài khoản phải giữ role USER (chốt 2026-10-03). */
    public static final String ROLE_USER_REQUIRED = "ROLE_USER_REQUIRED";
    /** OWNER chỉ gán qua duyệt yêu cầu sở hữu (UC30), không gán / gỡ tay (chốt O6 2026-10-03). */
    public static final String OWNER_ROLE_MANAGED_BY_CLAIM = "OWNER_ROLE_MANAGED_BY_CLAIM";

    private ErrorCode() {}
}
