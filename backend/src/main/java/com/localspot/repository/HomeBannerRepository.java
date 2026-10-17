package com.localspot.repository;

import com.localspot.entity.BannerSlot;
import com.localspot.entity.HomeBanner;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {

    Optional<HomeBanner> findBySlot(BannerSlot slot);

    /** Khóa dòng của ô khi thay ảnh — hai quản trị viên cùng thay thì người sau chờ, ảnh cũ của người trước được dọn. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM HomeBanner b WHERE b.slot = :slot")
    Optional<HomeBanner> findBySlotForUpdate(@Param("slot") BannerSlot slot);

    /** Ảnh trang chủ đang bật — một truy vấn cho {@code GET /home}. */
    List<HomeBanner> findByActiveTrue();
}
