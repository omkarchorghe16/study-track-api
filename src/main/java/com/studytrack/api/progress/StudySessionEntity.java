package com.studytrack.api.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Where the user left off in one subject. */
@Entity
@Table(name = "study_sessions")
public class StudySessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private Long subjectId;

    @Column(name = "last_topic_id")
    private Long lastTopicId;

    @Column(name = "last_item_id")
    private Long lastItemId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudySessionEntity() {
    }

    public StudySessionEntity(Long userId, Long subjectId) {
        this.userId = userId;
        this.subjectId = subjectId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Long getLastTopicId() {
        return lastTopicId;
    }

    public Long getLastItemId() {
        return lastItemId;
    }

    /**
     * Points the session at a topic and, optionally, an item in it. The time is always updated, so
     * opening the same item again makes this subject the most recent one.
     */
    public void moveTo(Long topicId, Long itemId, Instant openedAt) {
        this.lastTopicId = topicId;
        this.lastItemId = itemId;
        this.updatedAt = openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
