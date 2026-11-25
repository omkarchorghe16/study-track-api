package com.studytrack.api.progress;

import com.studytrack.api.item.ItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "item_progress")
public class ItemProgressEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status = ItemStatus.NOT_STARTED;

    private Short confidence;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Column(nullable = false)
    private boolean bookmarked;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ItemProgressEntity() {
    }

    public ItemProgressEntity(Long userId, Long itemId) {
        this.userId = userId;
        this.itemId = itemId;
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public Short getConfidence() {
        return confidence;
    }

    public void setConfidence(Short confidence) {
        this.confidence = confidence;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public Instant getLastReviewedAt() {
        return lastReviewedAt;
    }

    public Instant getNextReviewAt() {
        return nextReviewAt;
    }

    /** Stores one flashcard review: increments the count and sets the review times. */
    public void recordReview(Instant reviewedAt, Instant nextReviewAt) {
        this.reviewCount++;
        this.lastReviewedAt = reviewedAt;
        this.nextReviewAt = nextReviewAt;
    }

    public boolean isBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
