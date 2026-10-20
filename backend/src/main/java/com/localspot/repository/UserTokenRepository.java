package com.localspot.repository;

import com.localspot.entity.UserToken;
import com.localspot.entity.UserTokenType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHashAndType(String tokenHash, UserTokenType type);

    /** Dùng một lần, nguyên tử: hai lần bấm link đồng thời chỉ một lần thành công. */
    @Modifying
    @Query("UPDATE UserToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Vô hiệu các link cũ chưa dùng cùng loại khi cấp link mới (UC01 7a) — chỉ link mới nhất có hiệu lực. Đánh dấu
     * {@code used_at} thay vì xóa: giữ dấu vết, không xóa dữ liệu.
     */
    @Modifying
    @Query("UPDATE UserToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.type = :type AND t.usedAt IS NULL")
    int invalidateUnused(@Param("userId") Long userId, @Param("type") UserTokenType type, @Param("now") Instant now);
}
