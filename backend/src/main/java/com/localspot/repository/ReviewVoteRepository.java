package com.localspot.repository;

import com.localspot.entity.ReviewVote;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, ReviewVote.Id> {

    /** Review nào trong trang người xem đã vote "hữu ích" ({@code votedByMe}) — một truy vấn cho cả trang. */
    @Query("SELECT v.id.reviewId FROM ReviewVote v WHERE v.id.userId = :userId AND v.id.reviewId IN :reviewIds")
    List<Long> findVotedReviewIds(@Param("userId") Long userId, @Param("reviewIds") Collection<Long> reviewIds);
}
