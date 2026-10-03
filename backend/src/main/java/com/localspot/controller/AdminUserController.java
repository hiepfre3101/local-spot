package com.localspot.controller;

import com.localspot.dto.request.LockUserRequest;
import com.localspot.dto.response.AdminUserResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.Permissions;
import com.localspot.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/admin/users} — quản lý người dùng & vai trò (UC31, FR-38). Quyền theo {@code x-permission}. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUsers;

    public AdminUserController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.USER_VIEW + "')")
    public CursorPage<AdminUserResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean locked,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return adminUsers.search(q, role, locked, cursor, limit);
    }

    @PostMapping("/{userId}/lock")
    @PreAuthorize("hasAuthority('" + Permissions.USER_LOCK + "')")
    public ResponseEntity<Void> lock(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long userId,
            @Valid @RequestBody LockUserRequest request) {
        adminUsers.lock(actor.id(), userId, request.until(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/lock")
    @PreAuthorize("hasAuthority('" + Permissions.USER_LOCK + "')")
    public ResponseEntity<Void> unlock(@PathVariable Long userId) {
        adminUsers.unlock(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('" + Permissions.USER_ASSIGN_ROLE + "')")
    public ResponseEntity<Void> replaceRoles(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long userId,
            @RequestBody List<String> roles) {
        adminUsers.replaceRoles(actor.id(), userId, roles);
        return ResponseEntity.noContent().build();
    }
}
