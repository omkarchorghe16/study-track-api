package com.studytrack.api.catalog;

import com.studytrack.api.item.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A starter question with its answer under a catalog topic; both are plain text. */
@Entity
@Table(name = "catalog_questions")
public class CatalogQuestionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_topic_id", nullable = false, updatable = false)
    private Long catalogTopicId;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CatalogQuestionEntity() {
    }

    public CatalogQuestionEntity(Long catalogTopicId, String question, String answer, Difficulty difficulty,
                                 int sortOrder) {
        this.catalogTopicId = catalogTopicId;
        this.question = question;
        this.answer = answer;
        this.difficulty = difficulty;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getCatalogTopicId() {
        return catalogTopicId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
