package com.localspot.controller;

import com.localspot.dto.request.CheckInRequest;
import com.localspot.dto.response.CheckInResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.CheckInService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Check-in (UC16). Lịch sử của chính mình ở {@code GET /me/check-ins} ({@link MeController}). */
@RestController
@RequestMapping("/api/v1")
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/places/{placeId}/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInResponse checkIn(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long placeId,
            @Valid @RequestBody CheckInRequest request) {
        return checkInService.checkIn(user.id(), placeId, request);
    }
}
