package com.localspot.mapper;

import com.localspot.dto.response.CommentResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.Comment;
import com.localspot.entity.User;
import java.util.List;
import org.mapstruct.Mapper;

/** Bình luận đang hiện → {@link CommentResponse}; placeholder của gốc đã xóa do {@code CommentService} dựng. */
@Mapper
public interface CommentMapper {

    UserSummary toUserSummary(User user);

    /** {@code replies}: danh sách (có thể rỗng) cho bình luận gốc, {@code null} cho trả lời. */
    default CommentResponse toResponse(Comment comment, List<CommentResponse> replies) {
        return new CommentResponse(
                comment.getId(),
                toUserSummary(comment.getUser()),
                comment.getContent(),
                comment.isRoot() ? null : comment.getParent().getId(), // proxy: đọc id không nạp bản ghi cha
                false,
                replies,
                comment.getCreatedAt());
    }
}
