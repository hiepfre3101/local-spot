package com.localspot.repository;

import com.localspot.entity.PhotoStatus;
import com.localspot.entity.ReviewPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewPhotoRepository extends JpaRepository<ReviewPhoto, Long> {

    /** Ảnh của cả trang review — một truy vấn, theo thứ tự hiển thị. Index {@code (review_id, sort_order)}. */
    @Query("SELECT p FROM ReviewPhoto p WHERE p.review.id IN :reviewIds ORDER BY p.sortOrder, p.id")
    List<ReviewPhoto> findByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);

    Optional<ReviewPhoto> findByIdAndReviewId(Long id, Long reviewId);

    /** Ảnh đang tính vào giới hạn mỗi review — ảnh lỗi không tính (người dùng tải lại được). */
    long countByReviewIdAndStatusNot(Long reviewId, PhotoStatus status);

    @Query("SELECT COALESCE(MAX(p.sortOrder), -1) FROM ReviewPhoto p WHERE p.review.id = :reviewId")
    int maxSortOrder(@Param("reviewId") Long reviewId);
}
