package com.localspot.mapper;

import com.localspot.dto.response.UserSummary;
import com.localspot.entity.User;
import org.mapstruct.Mapper;

/** Chủ bộ sưu tập → {@link UserSummary}; phần còn lại do {@code CollectionService} dựng (lọc theo người xem). */
@Mapper
public interface CollectionMapper {

    UserSummary toUserSummary(User user);
}
