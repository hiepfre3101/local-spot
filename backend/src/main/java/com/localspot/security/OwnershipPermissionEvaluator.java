package com.localspot.security;

import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.CommentRepository;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.ReviewRepository;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Quyền trên một bản ghi cụ thể (phần lõi — CLAUDE.md §3): permission {@code *-own} chỉ có nghĩa khi người gọi đúng là
 * chủ của bản ghi. Dùng trong {@code @PreAuthorize} theo id — không nạp entity:
 *
 * <pre>{@code @PreAuthorize("hasPermission(#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
 *         + Permissions.REVIEW_UPDATE_OWN + "')")}</pre>
 *
 * Thứ tự kiểm tra:
 *
 * <ol>
 *   <li>Người gọi có authority đó không (RBAC) — không có → {@code false} (403), không cần chạm CSDL.
 *   <li>Bản ghi còn tồn tại không (bỏ qua bản xóa mềm) — không → 404 với mã theo loại. Nội dung review / bình luận /
 *       địa điểm vốn công khai nên phân biệt 404 với 403 không lộ gì đáng kể, đổi lại client nhận đúng ngữ nghĩa REST
 *       (xóa lần hai → 404, không phải 403 khó hiểu).
 *   <li>Người gọi có phải chủ không — một truy vấn chỉ đọc cột khóa ngoại.
 * </ol>
 *
 * <b>Không có đường vượt quyền</b> cho moderator / admin (chốt 2026-10-03): nhân sự xử lý nội dung của người khác qua
 * endpoint {@code /moderation} riêng — có ghi nhật ký, có tính vi phạm vào trust — nên không được âm thầm sửa nội dung
 * qua endpoint của chủ. Luật nghiệp vụ theo trạng thái (vd. sửa review đang HIDDEN) thuộc service, không thuộc lớp này.
 *
 * <p>Gọi với permission / loại đối tượng không có trong bảng luật là lỗi lập trình → ném ngoại lệ (500, test bắt được)
 * thay vì âm thầm trả {@code false}.
 */
@Component
public final class OwnershipPermissionEvaluator implements PermissionEvaluator {

    public static final String PLACE = "Place";
    public static final String REVIEW = "Review";
    public static final String COMMENT = "Comment";

    private final Map<String, Rule> rules;

    public OwnershipPermissionEvaluator(PlaceRepository places, ReviewRepository reviews, CommentRepository comments) {
        Rule placeOwner = new Rule(PLACE, places::isOwnedBy, ErrorCode.PLACE_NOT_FOUND, "Không tìm thấy địa điểm.");
        Rule reviewAuthor =
                new Rule(REVIEW, reviews::isAuthoredBy, ErrorCode.REVIEW_NOT_FOUND, "Không tìm thấy đánh giá.");
        this.rules = Map.of(
                Permissions.REVIEW_UPDATE_OWN, reviewAuthor,
                Permissions.REVIEW_DELETE_OWN, reviewAuthor,
                Permissions.COMMENT_DELETE_OWN,
                        new Rule(
                                COMMENT,
                                comments::isAuthoredBy,
                                ErrorCode.COMMENT_NOT_FOUND,
                                "Không tìm thấy bình luận."),
                Permissions.PLACE_UPDATE_OWN, placeOwner,
                Permissions.PLACE_STATS_OWN, placeOwner,
                Permissions.REVIEW_REPLY_OWN_PLACE,
                        new Rule(
                                REVIEW,
                                reviews::isOnPlaceOwnedBy,
                                ErrorCode.REVIEW_NOT_FOUND,
                                "Không tìm thấy đánh giá."));
    }

    /** Permission nào được kiểm tra quyền sở hữu ở đây — {@code OwnershipPermissionEvaluatorTests} dùng để đối chiếu. */
    public Map<String, String> targetTypeByPermission() {
        return rules.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> e.getValue().targetType()));
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        Rule rule = rules.get(String.valueOf(permission));
        if (rule == null) {
            throw new IllegalArgumentException("Không phải permission sở hữu: " + permission);
        }
        if (!rule.targetType().equals(targetType)) {
            throw new IllegalArgumentException(
                    permission + " áp dụng cho " + rule.targetType() + ", không phải " + targetType);
        }
        if (!(targetId instanceof Long id)) {
            throw new IllegalArgumentException("Id đối tượng phải là Long, nhận: " + targetId);
        }
        if (auth == null
                || !(auth.getPrincipal() instanceof AuthenticatedUser user)
                || !hasAuthority(auth, permission)) {
            return false;
        }
        return rule.check()
                .apply(id, user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, rule.notFoundCode(), rule.notFoundDetail()));
    }

    /** Dạng truyền cả entity không dùng: kiểm tra theo id tránh nạp entity chỉ để phân quyền. */
    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        throw new UnsupportedOperationException("Dùng hasPermission(id, loại, permission)");
    }

    private static boolean hasAuthority(Authentication auth, Object permission) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (permission.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private record Rule(
            String targetType,
            BiFunction<Long, Long, Optional<Boolean>> check,
            String notFoundCode,
            String notFoundDetail) {}
}
