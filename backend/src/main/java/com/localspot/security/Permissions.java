package com.localspot.security;

import java.util.Set;

/**
 * Tên permission (authority dạng {@code resource:action}) — đúng 17 giá trị seed ở {@code V2__seed_rbac.sql} và
 * {@code x-permission} trong openapi. {@code PermissionCatalogTests} khẳng định ba nơi khớp nhau.
 *
 * <p>Dùng trong {@code @PreAuthorize} bằng cách nối hằng số — gõ sai tên là lỗi biên dịch, thay vì một chuỗi
 * {@code hasAuthority('user:lok')} âm thầm chặn mọi người:
 *
 * <pre>{@code @PreAuthorize("hasAuthority('" + Permissions.USER_LOCK + "')")}</pre>
 *
 * Phân quyền theo permission chứ không theo role: đổi quyền của một role chỉ cần migration dữ liệu, không sửa code.
 */
public final class Permissions {

    // Thành viên
    public static final String REVIEW_CREATE = "review:create";
    public static final String REVIEW_UPDATE_OWN = "review:update-own";
    public static final String REVIEW_DELETE_OWN = "review:delete-own";
    public static final String COMMENT_DELETE_OWN = "comment:delete-own";

    // Chủ địa điểm
    public static final String PLACE_UPDATE_OWN = "place:update-own";
    public static final String PLACE_STATS_OWN = "place:stats-own";
    public static final String REVIEW_REPLY_OWN_PLACE = "review:reply-own-place";

    // Kiểm duyệt viên
    public static final String PLACE_APPROVE = "place:approve";
    public static final String REVIEW_MODERATE = "review:moderate";
    public static final String REPORT_HANDLE = "report:handle";
    public static final String CLAIM_APPROVE = "claim:approve";

    // Quản trị viên
    public static final String USER_VIEW = "user:view";
    public static final String USER_LOCK = "user:lock";
    public static final String USER_ASSIGN_ROLE = "user:assign-role";
    public static final String CATEGORY_MANAGE = "category:manage";
    public static final String AMENITY_MANAGE = "amenity:manage";
    public static final String DASHBOARD_VIEW = "dashboard:view";

    public static final Set<String> ALL = Set.of(
            REVIEW_CREATE,
            REVIEW_UPDATE_OWN,
            REVIEW_DELETE_OWN,
            COMMENT_DELETE_OWN,
            PLACE_UPDATE_OWN,
            PLACE_STATS_OWN,
            REVIEW_REPLY_OWN_PLACE,
            PLACE_APPROVE,
            REVIEW_MODERATE,
            REPORT_HANDLE,
            CLAIM_APPROVE,
            USER_VIEW,
            USER_LOCK,
            USER_ASSIGN_ROLE,
            CATEGORY_MANAGE,
            AMENITY_MANAGE,
            DASHBOARD_VIEW);

    private Permissions() {}
}
