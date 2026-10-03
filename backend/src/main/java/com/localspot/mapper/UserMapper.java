package com.localspot.mapper;

import com.localspot.dto.response.AdminUserResponse;
import com.localspot.dto.response.MeResponse;
import com.localspot.entity.Role;
import com.localspot.entity.User;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    /** Phần dẫn xuất (trust, địa điểm sở hữu) do service tính rồi truyền vào — mapper không truy vấn. */
    MeResponse toMeResponse(User user, int trustScore, List<Long> ownedPlaceIds);

    AdminUserResponse toAdminUserResponse(User user, int trustScore);

    default List<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).sorted().toList();
    }
}
