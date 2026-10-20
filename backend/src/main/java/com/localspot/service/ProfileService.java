package com.localspot.service;

import com.localspot.dto.response.MeResponse;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.UserMapper;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hồ sơ của chính người dùng ({@code MeResponse}). */
@Service
public class ProfileService {

    private final UserRepository users;
    private final PlaceRepository places;
    private final TrustScoreService trustScores;
    private final UserMapper mapper;

    public ProfileService(
            UserRepository users, PlaceRepository places, TrustScoreService trustScores, UserMapper mapper) {
        this.users = users;
        this.places = places;
        this.trustScores = trustScores;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = users.findWithRolesById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Tài khoản không còn tồn tại."));
        return toMeResponse(user);
    }

    /** Dùng khi đã có entity kèm roles (đăng nhập, refresh) — tránh nạp lại người dùng. */
    @Transactional(readOnly = true)
    public MeResponse toMeResponse(User user) {
        return mapper.toMeResponse(user, trustScores.trustScoreOf(user), places.findIdsByOwnerId(user.getId()));
    }
}
