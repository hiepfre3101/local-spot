package com.localspot.mapper;

import com.localspot.dto.response.OwnerReplyResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.OwnerReply;
import com.localspot.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Phần ánh xạ thẳng của review; các trường theo người xem / đếm theo lô do {@code ReviewViews} dựng. */
@Mapper
public interface ReviewMapper {

    UserSummary toUserSummary(User user);

    @Mapping(target = "owner", source = "user")
    OwnerReplyResponse toOwnerReply(OwnerReply reply);
}
