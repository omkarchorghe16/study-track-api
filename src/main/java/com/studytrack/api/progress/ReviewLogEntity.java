package com.studytrack.api.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "flashcard_reviews")
public class ReviewLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ReviewResult result;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    protected ReviewLogEntity() {
    }

    public ReviewLogEntity(Long userId, Long itemId, ReviewResult result, Instant reviewedAt) {
        this.userId = userId;
        this.itemId = itemId;
        this.result = result;
        this.reviewedAt = reviewedAt;
    }

    public ReviewResult getResult() {
        return result;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
