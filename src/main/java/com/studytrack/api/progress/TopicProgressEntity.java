package com.studytrack.api.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "topic_progress")
public class TopicProgressEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private Long topicId;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TopicProgressEntity() {
    }

    public TopicProgressEntity(Long userId, Long topicId) {
        this.userId = userId;
        this.topicId = topicId;
    }

    public Long getTopicId() {
        return topicId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /** Marks the topic complete at {@code completedAt}, or clears the mark when it is null. */
    public void setCompletedAt(Instant completedAt) {
        this.completed = completedAt != null;
        this.completedAt = completedAt;
    }
}
