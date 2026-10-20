package com.localspot.service;

import com.localspot.dto.request.CommentCreateRequest;
import com.localspot.dto.response.CommentResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.entity.Comment;
import com.localspot.entity.CommentStatus;
import com.localspot.entity.Review;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.CommentMapper;
import com.localspot.repository.CommentRepository;
import com.localspot.repository.ReviewRepository;
import com.localspot.repository.UserRepository;
import com.localspot.security.RateLimitPolicy;
import com.localspot.security.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bình luận trên review (UC15, FR-21) — 2 cấp (Q5): bình luận gốc và trả lời cho gốc, không trả lời một trả lời. Chỉ
 * bình luận trên review đang hiện (PUBLISHED, địa điểm APPROVED); viết cần email đã xác thực (chốt 2026-10-11).
 *
 * <p><b>Xóa gốc còn trả lời</b> (chốt 2026-10-11): gốc xóa mềm như mọi bình luận nhưng vẫn trả về dạng placeholder
 * ({@code deleted = true}, không tác giả / nội dung) khi còn trả lời đang hiện — không làm mất lời của người khác chỉ vì
 * người viết gốc rút lại. Gốc bị ẩn sau báo cáo (HIDDEN) xử lý giống hệt. Không còn trả lời nào thì gốc biến mất.
 *
 * <p><b>Thứ tự</b>: gốc cũ trước (đọc như hội thoại), phân trang keyset theo id tăng; mỗi gốc kèm toàn bộ trả lời, cũ
 * trước — không phân trang trả lời (quy mô đồ án, chốt 2026-10-11).
 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final CommentMapper mapper;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public CommentService(
            CommentRepository comments,
            ReviewRepository reviews,
            UserRepository users,
            CommentMapper mapper,
            RateLimiter rateLimiter,
            Clock clock) {
        this.comments = comments;
        this.reviews = reviews;
        this.users = users;
        this.mapper = mapper;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // ─── Danh sách ───────────────────────────────────────────────────────────

    /** Ba truy vấn cho cả trang bất kể số gốc: trang gốc (native) → gốc đang hiện + tác giả → trả lời + tác giả. */
    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> list(Long reviewId, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        Long afterId = KeysetCursor.decode(cursor);
        reviews.findVisibleById(reviewId).orElseThrow(ReviewService::reviewNotFound);

        List<RootRow> rows = comments.findRootPage(reviewId, afterId, pageSize + 1).stream()
                .map(RootRow::of)
                .toList();
        boolean hasMore = rows.size() > pageSize;
        List<RootRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }

        List<Long> rootIds = page.stream().map(RootRow::id).toList();
        List<Long> shownIds =
                page.stream().filter(RootRow::shown).map(RootRow::id).toList();
        Map<Long, Comment> shownRoots = shownIds.isEmpty()
                ? Map.of()
                : comments.findVisibleWithAuthorByIdIn(shownIds).stream()
                        .collect(Collectors.toMap(Comment::getId, Function.identity()));
        Map<Long, List<CommentResponse>> repliesByRoot = new LinkedHashMap<>();
        for (Comment reply : comments.findVisibleRepliesWithAuthor(rootIds)) {
            repliesByRoot
                    .computeIfAbsent(reply.getParent().getId(), id -> new ArrayList<>())
                    .add(mapper.toResponse(reply, null));
        }

        List<CommentResponse> items = new ArrayList<>(page.size());
        for (RootRow row : page) {
            List<CommentResponse> replies = repliesByRoot.getOrDefault(row.id(), List.of());
            Comment root = shownRoots.get(row.id());
            if (root != null) {
                items.add(mapper.toResponse(root, replies));
            } else if (!replies.isEmpty()) {
                // Gốc đã xóa / bị ẩn giữa truy vấn trang và truy vấn nạp: chỉ giữ khi còn trả lời
                items.add(new CommentResponse(row.id(), null, null, null, true, replies, row.createdAt()));
            }
        }
        String next = hasMore ? KeysetCursor.encode(page.getLast().id()) : null;
        return new CursorPage<>(items, next);
    }

    // ─── Viết ───────────────────────────────────────────────────────────────

    /**
     * Thứ tự kiểm tra: email đã xác thực (403) → nội dung không rỗng sau khi bỏ khoảng trắng (422) → review đang hiện
     * (404) → {@code parentId} là bình luận gốc đang hiện của cùng review (422).
     */
    @Transactional
    public CommentResponse create(Long userId, Long reviewId, CommentCreateRequest request) {
        User author = users.findById(userId).orElseThrow(CommentService::userNotFound);
        if (!author.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED, "Hãy xác thực email trước khi bình luận.");
        }
        String content = request.content().strip();
        if (content.isEmpty()) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED, "content", "Nội dung bình luận không được để trống.");
        }
        Review review = reviews.findVisibleById(reviewId).orElseThrow(ReviewService::reviewNotFound);
        Comment parent = request.parentId() == null ? null : checkParent(request.parentId(), reviewId);
        // Sau mọi kiểm tra: gõ sai / review đã ẩn không tiêu suất; vượt ngưỡng → 429 + Retry-After
        rateLimiter.acquire(RateLimitPolicy.COMMENT, userId.toString());

        Comment comment = comments.saveAndFlush(new Comment(review, author, parent, content)); // có createdAt
        return mapper.toResponse(comment, parent == null ? List.of() : null);
    }

    /** Lỗi theo trường {@code parentId} (422) — đây là dữ liệu trong body, không phải tài nguyên trên đường dẫn. */
    private Comment checkParent(Long parentId, Long reviewId) {
        Comment parent = comments.findById(parentId)
                .filter(c -> c.getStatus() == CommentStatus.VISIBLE)
                .filter(c -> c.getReview().getId().equals(reviewId))
                .orElseThrow(() -> ApiException.fieldError(
                        ErrorCode.VALIDATION_FAILED, "parentId", "Bình luận được trả lời không còn tồn tại."));
        if (!parent.isRoot()) {
            throw ApiException.fieldError(
                    ErrorCode.COMMENT_DEPTH_EXCEEDED,
                    "parentId",
                    "Chỉ trả lời được bình luận gốc (bình luận tối đa 2 cấp).");
        }
        return parent;
    }

    // ─── Xóa ────────────────────────────────────────────────────────────────

    /**
     * Xóa mềm bình luận của mình — quyền tác giả kiểm ở {@code @PreAuthorize}. Luôn được xóa (kể cả khi review đã bị
     * ẩn): rút lại lời của mình không gây hại gì. Trả lời của gốc giữ nguyên (placeholder — xem đầu lớp).
     */
    @Transactional
    public void delete(Long commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(CommentService::commentNotFound);
        comment.setDeletedAt(clock.instant());
    }

    private record RootRow(Long id, boolean shown, Instant createdAt) {

        static RootRow of(Object[] row) {
            return new RootRow(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).intValue() == 1,
                    Instant.ofEpochMilli(((Number) row[2]).longValue()));
        }
    }

    private static ApiException commentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.COMMENT_NOT_FOUND, "Không tìm thấy bình luận.");
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng.");
    }
}
