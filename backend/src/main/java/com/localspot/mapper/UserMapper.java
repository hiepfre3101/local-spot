package com.localspot.mapper;

import com.localspot.dto.response.AdminUserResponse;
import com.localspot.dto.response.MeResponse;
import com.localspot.entity.Permission;
import com.localspot.entity.Role;
import com.localspot.entity.User;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper
public interface UserMapper {

    /**
     * Phần dẫn xuất (trust, địa điểm sở hữu) do service tính rồi truyền vào — mapper không truy vấn. {@code permissions}
     * gộp từ các role (đã nạp sẵn qua entity graph) để frontend ẩn / hiện và chặn route theo đúng permission như
     * {@code @PreAuthorize} của backend, không tự suy ra quyền từ tên role.
     */
    @Mapping(target = "permissions", source = "user.roles", qualifiedByName = "permissionNames")
    MeResponse toMeResponse(User user, int trustScore, List<Long> ownedPlaceIds);

    AdminUserResponse toAdminUserResponse(User user, int trustScore);

    default List<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).sorted().toList();
    }

    @Named("permissionNames")
    default List<String> permissionNames(Set<Role> roles) {
        return roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName)
                .distinct()
                .sorted()
                .toList();
    }
}
