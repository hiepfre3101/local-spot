package com.localspot.controller;

import com.localspot.dto.request.ClaimCreateRequest;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.ClaimService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code POST /api/v1/places/{placeId}/claims} — thành viên yêu cầu xác nhận sở hữu (UC23, FR-31). Chỉ cần đăng nhập (như
 * check-in / báo cáo); email đã xác thực kiểm ở service.
 */
@RestController
@RequestMapping("/api/v1/places/{placeId}/claims")
public class ClaimController {

    private final ClaimService claims;

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long placeId,
            @Valid @RequestPart("claim") ClaimCreateRequest claim,
            @RequestPart(value = "evidence", required = false) List<MultipartFile> evidence) {
        claims.create(user.id(), placeId, claim, evidence);
    }
}
