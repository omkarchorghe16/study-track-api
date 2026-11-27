package com.studytrack.api.explanation;

import com.studytrack.api.llm.LlmProvider;
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

/** The AI-written explanation of one question; {@code content} is the JSON of an ExplanationContent. */
@Entity
@Table(name = "question_explanations")
public class QuestionExplanationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_provider", nullable = false, length = 20)
    private LlmProvider llmProvider;

    @Column(name = "llm_model", nullable = false, length = 100)
    private String llmModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuestionExplanationEntity() {
    }

    public QuestionExplanationEntity(Long questionId, Long userId) {
        this.questionId = questionId;
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Replaces the text and records which model wrote it. */
    public void replaceContent(String newContent, LlmProvider provider, String model) {
        this.content = newContent;
        this.llmProvider = provider;
        this.llmModel = model;
    }

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public LlmProvider getLlmProvider() {
        return llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
