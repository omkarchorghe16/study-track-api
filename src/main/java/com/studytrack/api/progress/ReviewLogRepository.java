package com.studytrack.api.progress;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ReviewLogRepository extends JpaRepository<ReviewLogEntity, Long> {

    /** The item's latest results, newest first. */
    @Query("""
            select r.result from ReviewLogEntity r
            where r.userId = :userId and r.itemId = :itemId
            order by r.reviewedAt desc, r.id desc
            """)
    List<ReviewResult> findLatestResults(Long userId, Long itemId, Pageable pageable);

    @Query("select r.reviewedAt from ReviewLogEntity r where r.userId = :userId and r.reviewedAt >= :from")
    List<Instant> findReviewTimes(Long userId, Instant from);
}
