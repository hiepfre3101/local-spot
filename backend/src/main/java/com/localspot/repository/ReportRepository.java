package com.localspot.repository;

import com.localspot.entity.Report;
import com.localspot.entity.ReportTargetType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** U10: một báo cáo / người / đối tượng, kể cả báo cáo đã xử lý — UNIQUE chặn nốt request chen. */
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    /**
     * Hàng đợi UC29: gom báo cáo OPEN theo đối tượng, FIFO theo báo cáo đầu tiên (index {@code (status, target_type,
     * target_id)}). Keyset trên {@code MIN(id)} — không trùng / sót nhóm khi có báo cáo mới giữa hai lần tải.
     */
    @Query("""
            SELECT new com.localspot.repository.ReportGroupRow(
                       r.targetType, r.targetId, COUNT(r), MIN(r.createdAt), MIN(r.id))
            FROM Report r
            WHERE r.status = com.localspot.entity.ReportStatus.OPEN
              AND (:targetType IS NULL OR r.targetType = :targetType)
            GROUP BY r.targetType, r.targetId
            HAVING (:afterId IS NULL OR MIN(r.id) > :afterId)
            ORDER BY MIN(r.id)
            """)
    List<ReportGroupRow> findOpenGroups(
            @Param("targetType") ReportTargetType targetType, @Param("afterId") Long afterId, Limit limit);

    /** Lý do của các nhóm trên một trang — lọc theo id rồi khớp loại ở service (JPQL không có IN theo cặp). */
    @Query("""
            SELECT new com.localspot.repository.ReportReasonCount(r.targetType, r.targetId, r.reason, COUNT(r))
            FROM Report r
            WHERE r.status = com.localspot.entity.ReportStatus.OPEN AND r.targetId IN :targetIds
            GROUP BY r.targetType, r.targetId, r.reason
            """)
    List<ReportReasonCount> countOpenReasons(@Param("targetIds") Collection<Long> targetIds);

    /** Chi tiết nhóm: báo cáo đang mở kèm người báo cáo, cũ trước. */
    @Query("""
            SELECT r FROM Report r JOIN FETCH r.reporter
            WHERE r.targetType = :targetType AND r.targetId = :targetId
              AND r.status = com.localspot.entity.ReportStatus.OPEN
            ORDER BY r.id
            """)
    List<Report> findOpenWithReporter(
            @Param("targetType") ReportTargetType targetType, @Param("targetId") Long targetId);

    /**
     * Khóa các báo cáo đang mở của một đối tượng ({@code SELECT … FOR UPDATE}) khi xử lý: hai kiểm duyệt viên quyết
     * định cùng lúc thì người sau chờ, rồi đọc lại thấy không còn báo cáo mở → 409 thay vì ghi hai vi phạm.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM Report r
            WHERE r.targetType = :targetType AND r.targetId = :targetId
              AND r.status = com.localspot.entity.ReportStatus.OPEN
            ORDER BY r.id
            """)
    List<Report> findOpenForUpdate(@Param("targetType") ReportTargetType targetType, @Param("targetId") Long targetId);
}
