package com.studytrack.api.importer;

import com.studytrack.api.item.Difficulty;
import com.studytrack.api.item.ItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** A question, note or example found by the LLM, waiting for review. HTML fields are already sanitised. */
@Entity
@Table(name = "import_candidates")
public class ImportCandidateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    @Column(name = "chunk_index", nullable = false, updatable = false)
    private int chunkIndex;

    @Column(name = "suggested_topic_path", length = 500)
    private String suggestedTopicPath;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(length = 1000)
    private String tags;

    @Column(name = "image_refs", length = 1000)
    private String imageRefs;

    @Column(name = "confidence_score", precision = 4, scale = 3)
    private BigDecimal confidenceScore;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CandidateStatus status;

    @Column(name = "target_topic_id")
    private Long targetTopicId;

    @Column(name = "created_item_id")
    private Long createdItemId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ImportCandidateEntity() {
    }

    public ImportCandidateEntity(Long jobId, int chunkIndex) {
        this.jobId = jobId;
        this.chunkIndex = chunkIndex;
        this.status = CandidateStatus.PENDING;
    }

    public List<String> getTagList() {
        return splitList(tags);
    }

    public void setTagList(List<String> tagNames) {
        tags = tagNames == null || tagNames.isEmpty() ? null : String.join(",", tagNames);
    }

    public List<Long> getImageIdList() {
        return splitList(imageRefs).stream().map(Long::valueOf).toList();
    }

    public void setImageIdList(List<Long> imageIds) {
        imageRefs = imageIds == null || imageIds.isEmpty()
                ? null
                : String.join(",", imageIds.stream().map(String::valueOf).toList());
    }

    private static List<String> splitList(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(String::strip).filter(part -> !part.isEmpty()).toList();
    }

    public Long getId() {
        return id;
    }

    public Long getJobId() {
        return jobId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getSuggestedTopicPath() {
        return suggestedTopicPath;
    }

    public void setSuggestedTopicPath(String suggestedTopicPath) {
        this.suggestedTopicPath = suggestedTopicPath;
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

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public CandidateStatus getStatus() {
        return status;
    }

    public void setStatus(CandidateStatus status) {
        this.status = status;
    }

    public Long getTargetTopicId() {
        return targetTopicId;
    }

    public void setTargetTopicId(Long targetTopicId) {
        this.targetTopicId = targetTopicId;
    }

    public Long getCreatedItemId() {
        return createdItemId;
    }

    public void setCreatedItemId(Long createdItemId) {
        this.createdItemId = createdItemId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
