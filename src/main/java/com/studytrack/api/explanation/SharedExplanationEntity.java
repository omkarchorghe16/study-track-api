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

/** The AI-written explanation of a question, shared by all users; {@code content} is the JSON of an ExplanationContent. */
@Entity
@Table(name = "shared_explanations")
public class SharedExplanationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "explanation_key", nullable = false, updatable = false, length = 64)
    private String explanationKey;

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

    protected SharedExplanationEntity() {
    }

    public SharedExplanationEntity(String explanationKey, String content, LlmProvider llmProvider, String llmModel) {
        this.explanationKey = explanationKey;
        this.content = content;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
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

    public String getExplanationKey() {
        return explanationKey;
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
