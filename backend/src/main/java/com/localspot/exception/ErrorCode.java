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

    public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    public static final String AMENITY_NOT_FOUND = "AMENITY_NOT_FOUND";
    /** Không tồn tại, hoặc riêng tư / của người khác — không tiết lộ bộ sưu tập riêng tư có tồn tại (chốt 2026-10-12). */
    public static final String COLLECTION_NOT_FOUND = "COLLECTION_NOT_FOUND";
    /** 409 theo trường {@code slug}: slug danh mục / tiện ích đã có. */
    public static final String SLUG_TAKEN = "SLUG_TAKEN";
    /** 422 trường {@code parentId}: cây danh mục tối đa 2 cấp (chốt 2026-10-06). */
    public static final String CATEGORY_DEPTH_EXCEEDED = "CATEGORY_DEPTH_EXCEEDED";
    /** 409: danh mục còn danh mục con / địa điểm — không xóa kéo theo dữ liệu. */
    public static final String CATEGORY_IN_USE = "CATEGORY_IN_USE";
    /** 409: tiện ích đang gắn với địa điểm. */
    public static final String AMENITY_IN_USE = "AMENITY_IN_USE";
    /** 409: quyết định trên địa điểm không còn PENDING — đã được duyệt / từ chối (chốt 2026-10-04). */
    public static final String PLACE_ALREADY_MODERATED = "PLACE_ALREADY_MODERATED";
    /** Admin tự khóa mình / tự gỡ ADMIN của mình (chốt 2026-10-03); kiểm duyệt viên tự duyệt đề xuất của mình (2026-10-04). */
    public static final String SELF_ACTION_FORBIDDEN = "SELF_ACTION_FORBIDDEN";
    /** Mọi tài khoản phải giữ role USER (chốt 2026-10-03). */
    public static final String ROLE_USER_REQUIRED = "ROLE_USER_REQUIRED";
    /** OWNER chỉ gán qua duyệt yêu cầu sở hữu (UC30), không gán / gỡ tay (chốt O6 2026-10-03). */
    public static final String OWNER_ROLE_MANAGED_BY_CLAIM = "OWNER_ROLE_MANAGED_BY_CLAIM";

    /** 403: thao tác cần email đã xác thực — viết review (UC12 ngoại lệ 1a). */
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    /** 409: đã có review cho địa điểm này, kể cả review đã xóa (FR-17, D2) — gợi ý sửa review cũ. */
    public static final String REVIEW_ALREADY_EXISTS = "REVIEW_ALREADY_EXISTS";
    /** 409: quyết định trên review không còn PENDING — đã được duyệt / từ chối / ẩn (UC28, chốt 2026-10-15). */
    public static final String REVIEW_ALREADY_MODERATED = "REVIEW_ALREADY_MODERATED";
    /** 409: review đã bị ẩn sau báo cáo vi phạm — tác giả không sửa được (chốt 2026-10-08). */
    public static final String REVIEW_LOCKED = "REVIEW_LOCKED";
    /** 422 trường {@code parentId}: chỉ trả lời được bình luận gốc — bình luận tối đa 2 cấp (Q5). */
    public static final String COMMENT_DEPTH_EXCEEDED = "COMMENT_DEPTH_EXCEEDED";
    /** 422 trường {@code accuracyM}: GPS kém hơn 100 m — thường là vị trí ước lượng theo IP / wifi (U5, UC16 2b). */
    public static final String GPS_INACCURATE = "GPS_INACCURATE";
    /** 422: cách địa điểm quá 200 m (UC16 4a) — problem kèm {@code distanceM}. */
    public static final String CHECKIN_TOO_FAR = "CHECKIN_TOO_FAR";
    /** 409: đã check-in địa điểm này trong ngày (giờ Việt Nam) — 1 lần / địa điểm / ngày (U5, UC16 4b). */
    public static final String CHECKIN_ALREADY_TODAY = "CHECKIN_ALREADY_TODAY";
    /** 422: quá 50 bộ sưu tập mỗi người hoặc 100 địa điểm mỗi bộ (chốt 2026-10-12). */
    public static final String COLLECTION_LIMIT_EXCEEDED = "COLLECTION_LIMIT_EXCEEDED";
    /** 409: đã báo cáo đối tượng này — mỗi người một báo cáo / đối tượng, kể cả báo cáo đã xử lý (U10). */
    public static final String REPORT_ALREADY_EXISTS = "REPORT_ALREADY_EXISTS";
    /** 404: đối tượng không còn báo cáo nào đang mở (chi tiết nhóm trong hàng đợi UC29). */
    public static final String REPORT_NOT_FOUND = "REPORT_NOT_FOUND";
    /** 409: các báo cáo về đối tượng này đã được kiểm duyệt viên khác xử lý (UC29). */
    public static final String REPORT_ALREADY_HANDLED = "REPORT_ALREADY_HANDLED";

    private ErrorCode() {}
}
