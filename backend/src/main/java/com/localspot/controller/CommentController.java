package com.localspot.controller;

import com.localspot.dto.request.CommentCreateRequest;
import com.localspot.dto.response.CommentResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.OwnershipPermissionEvaluator;
import com.localspot.security.Permissions;
import com.localspot.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * openapi tag Comments (UC15). {@code GET} công khai (khai báo trong {@code SecurityConfig}). Viết bình luận không có
 * permission riêng trong RBAC (17 permission đã chốt) — mọi thành viên đăng nhập, điều kiện email kiểm ở service.
 */
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/reviews/{reviewId}/comments")
    public CursorPage<CommentResponse> list(
            @PathVariable Long reviewId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return commentService.list(reviewId, cursor, limit);
    }

    @PostMapping("/reviews/{reviewId}/comments")
    public ResponseEntity<CommentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long reviewId,
            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(201).body(commentService.create(user.id(), reviewId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("hasPermission(#commentId, '" + OwnershipPermissionEvaluator.COMMENT + "', '"
            + Permissions.COMMENT_DELETE_OWN + "')")
    public ResponseEntity<Void> delete(@PathVariable Long commentId) {
        commentService.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}
