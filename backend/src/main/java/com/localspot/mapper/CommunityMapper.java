package com.localspot.mapper;

import com.localspot.dto.response.BadgeResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.User;
import com.localspot.entity.UserBadge;
import org.mapstruct.Mapper;

/** Theo dõi, hồ sơ công khai, bảng xếp hạng (FR-28, FR-29). */
@Mapper
public interface CommunityMapper {

    UserSummary toUserSummary(User user);

    default BadgeResponse toBadge(UserBadge userBadge) {
        return new BadgeResponse(
                userBadge.getBadge().getCode(),
                userBadge.getBadge().getName(),
                userBadge.getBadge().getDescription(),
                userBadge.getBadge().getIcon(),
                userBadge.getAwardedAt());
    }
}
