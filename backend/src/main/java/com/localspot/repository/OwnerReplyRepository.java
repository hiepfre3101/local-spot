package com.localspot.repository;

import com.localspot.entity.OwnerReply;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnerReplyRepository extends JpaRepository<OwnerReply, Long> {

    /** Phản hồi của chủ (tối đa một / review — UNIQUE) cho cả trang review, kèm người phản hồi. */
    @Query("SELECT o FROM OwnerReply o JOIN FETCH o.user WHERE o.review.id IN :reviewIds")
    List<OwnerReply> findWithAuthorByReviewIdIn(@Param("reviewIds") Collection<Long> reviewIds);
}
