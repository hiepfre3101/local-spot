package com.localspot.controller;

import com.localspot.dto.request.ChangePasswordRequest;
import com.localspot.dto.response.CheckInResponse;
import com.localspot.dto.response.CollectionSummaryResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.MeResponse;
import com.localspot.dto.response.MyClaimResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.dto.response.ReviewResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.RefreshTokenCookies;
import com.localspot.service.AccountService;
import com.localspot.service.CheckInService;
import com.localspot.service.ClaimService;
import com.localspot.service.CollectionService;
import com.localspot.service.PlaceService;
import com.localspot.service.ProfileService;
import com.localspot.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/me} — openapi tag Me. Sửa hồ sơ, avatar, xóa tài khoản làm ở các mục sau. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ProfileService profileService;
    private final AccountService accountService;
    private final PlaceService placeService;
    private final ReviewService reviewService;
    private final CheckInService checkInService;
    private final CollectionService collectionService;
    private final ClaimService claimService;
    private final RefreshTokenCookies cookies;

    public MeController(
            ProfileService profileService,
            AccountService accountService,
            PlaceService placeService,
            ReviewService reviewService,
            CheckInService checkInService,
            CollectionService collectionService,
            ClaimService claimService,
            RefreshTokenCookies cookies) {
        this.profileService = profileService;
        this.accountService = accountService;
        this.placeService = placeService;
        this.reviewService = reviewService;
        this.checkInService = checkInService;
        this.collectionService = collectionService;
        this.claimService = claimService;
        this.cookies = cookies;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.me(user.id());
    }

    /** Địa điểm tôi đã đề xuất, mọi trạng thái — kèm trạng thái duyệt (UC11). */
    @GetMapping("/places")
    public CursorPage<PlaceSummaryResponse> myPlaces(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return placeService.proposedBy(user.id(), cursor, limit);
    }

    /** Review của tôi, mọi trạng thái — kèm lý do bị từ chối (UC12: tác giả thấy review chờ duyệt của mình). */
    @GetMapping("/reviews")
    public CursorPage<ReviewResponse> myReviews(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return reviewService.writtenBy(user.id(), cursor, limit);
    }

    /**
     * Bộ sưu tập của tôi, công khai lẫn riêng tư, mới tạo trước (≤ 50 nên không phân trang). {@code placeId}: kèm
     * {@code containsPlace} cho hộp "Lưu" trên P03.
     */
    @GetMapping("/collections")
    public List<CollectionSummaryResponse> myCollections(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required = false) Long placeId) {
        return collectionService.mine(user.id(), placeId);
    }

    /**
     * Lịch sử check-in của tôi, mới nhất trước. Chỉ chính chủ xem được — không có bản công khai {@code /users/{id}/…}
     * (chốt 2026-10-12: lịch sử check-in lộ nơi và lúc một người có mặt).
     */
    @GetMapping("/check-ins")
    public CursorPage<CheckInResponse> myCheckIns(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return checkInService.mine(user.id(), cursor, limit);
    }

    /**
     * Yêu cầu xác nhận sở hữu của tôi, mới nhất trước (P09 — thêm 2026-10-18): theo dõi kết quả duyệt, gửi lại khi bị
     * từ chối.
     */
    @GetMapping("/claims")
    public CursorPage<MyClaimResponse> myClaims(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return claimService.mine(user.id(), cursor, limit);
    }

    /**
     * Đổi mật khẩu (FR-06). Trả cookie refresh token mới: mọi token cũ đã bị thu hồi để đăng xuất các thiết bị khác,
     * thiết bị hiện tại nhận token mới nên vẫn giữ phiên.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest http) {
        String newRefreshToken = accountService.changePassword(
                user.id(), request.currentPassword(), request.newPassword(), ClientInfos.from(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(newRefreshToken).toString())
                .build();
    }
}
