package com.studytrack.api.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "study_items")
public class StudyItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "question_html")
    private String questionHtml;

    @Column(name = "answer_html")
    private String answerHtml;

    @Column(name = "notes_html")
    private String notesHtml;

    @Column(name = "answer_plain")
    private String answerPlain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ItemSource source;

    @Column(name = "import_job_id", updatable = false)
    private Long importJobId;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToMany
    @JoinTable(name = "study_item_tags",
            joinColumns = @JoinColumn(name = "item_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<TagEntity> tags = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudyItemEntity() {
    }

    public StudyItemEntity(ItemSource source) {
        this.source = source;
    }

    /** An item created by approving an import candidate of job {@code importJobId}. */
    public static StudyItemEntity fromImport(Long importJobId) {
        StudyItemEntity item = new StudyItemEntity(ItemSource.IMPORT);
        item.importJobId = importJobId;
        return item;
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

    public Long getId() {
        return id;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getQuestionHtml() {
        return questionHtml;
    }

    public void setQuestionHtml(String questionHtml) {
        this.questionHtml = questionHtml;
    }

    public String getAnswerHtml() {
        return answerHtml;
    }

    public void setAnswerHtml(String answerHtml) {
        this.answerHtml = answerHtml;
    }

    public String getNotesHtml() {
        return notesHtml;
    }

    public void setNotesHtml(String notesHtml) {
        this.notesHtml = notesHtml;
    }

    public String getAnswerPlain() {
        return answerPlain;
    }

    public void setAnswerPlain(String answerPlain) {
        this.answerPlain = answerPlain;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public ItemSource getSource() {
        return source;
    }

    public Long getImportJobId() {
        return importJobId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Set<TagEntity> getTags() {
        return tags;
    }

    public void replaceTags(Set<TagEntity> newTags) {
        tags.clear();
        tags.addAll(newTags);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
