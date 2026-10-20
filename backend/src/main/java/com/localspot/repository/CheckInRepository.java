package com.localspot.repository;

import com.localspot.entity.CheckIn;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    /** U5 — dùng {@code uk_check_ins_user_id_place_id_checkin_date}. */
    boolean existsByUserIdAndPlaceIdAndCheckinDate(Long userId, Long placeId, LocalDate checkinDate);

    /** {@code GET /me/check-ins}: mới nhất trước, keyset theo id; nạp sẵn địa điểm cho thẻ địa điểm. */
    @Query("""
            SELECT c FROM CheckIn c JOIN FETCH c.place p JOIN FETCH p.category
            WHERE c.user.id = :userId AND (:afterId IS NULL OR c.id < :afterId)
            ORDER BY c.id DESC
            """)
    List<CheckIn> findByUser(@Param("userId") Long userId, @Param("afterId") Long afterId, Limit limit);

    /**
     * Nhãn "đã check-in" trên review (sitemap P04, chốt 2026-10-12; mốc đổi 2026-10-19): tác giả có check-in tại địa
     * điểm vào ngày viết — hoặc ngày <b>sửa nội dung gần nhất</b> ({@code revised_at}, V6) — hay trước đó. So theo
     * <b>ngày</b> giờ Việt Nam (không theo giờ phút) vì người dùng hay viết review ngay tại quán rồi mới bấm check-in.
     * Check-in sau mốc đó không tính: review không "được xác nhận" ngược về sau; quay lại quán thì check-in rồi cập nhật
     * review theo lần ghé mới. Việt Nam không
     * có giờ mùa hè → lệch cố định +07:00, không cần bảng múi giờ của MySQL. Một truy vấn cho cả trang review.
     */
    @Query(value = """
            SELECT r.id FROM reviews r
            WHERE r.id IN (:reviewIds) AND EXISTS (
              SELECT 1 FROM check_ins c
              WHERE c.user_id = r.user_id AND c.place_id = r.place_id
                AND c.checkin_date <= DATE(CONVERT_TZ(COALESCE(r.revised_at, r.created_at), '+00:00', '+07:00')))
            """, nativeQuery = true)
    List<Long> findCheckedInReviewIds(@Param("reviewIds") Collection<Long> reviewIds);
}
