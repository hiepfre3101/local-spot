package com.localspot.repository;

import com.localspot.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Đánh dấu đã dùng một cách nguyên tử. Trả 0 nếu token đã bị dùng / thu hồi bởi request khác chen ngang — hai request
     * cùng gửi một token chỉ một request thắng, request còn lại bị coi là dùng lại (U2).
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** Thu hồi mọi token còn dùng được của người dùng (phát hiện dùng lại, đổi / đặt lại mật khẩu). */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now"
            + " WHERE t.user.id = :userId AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
