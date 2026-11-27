package com.studytrack.api.importer;

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

@Entity
@Table(name = "import_jobs")
public class ImportJobEntity {
    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private Long subjectId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private ImportFileType fileType;

    @Column(name = "uploaded_file_id")
    private Long mediaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_provider", length = 20)
    private LlmProvider llmProvider;

    @Column(name = "llm_model", length = 100)
    private String llmModel;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "processed_chunks", nullable = false)
    private int processedChunks;

    @Column(name = "failed_chunks", nullable = false)
    private int failedChunks;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "error_message", length = MAX_ERROR_LENGTH)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImportJobEntity() {
    }

    public ImportJobEntity(Long userId, Long subjectId) {
        this.userId = userId;
        this.subjectId = subjectId;
        this.status = ImportJobStatus.UPLOADED;
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

    /** Ends the job as FAILED with a message for the user (never document text or provider payloads). */
    public void markFailed(String message) {
        status = ImportJobStatus.FAILED;
        errorMessage = message == null || message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }

    /** Counts one finished chunk and the tokens its calls used. */
    public void recordChunk(boolean failed, long chunkInputTokens, long chunkOutputTokens) {
        processedChunks++;
        if (failed) {
            failedChunks++;
        }
        inputTokens += chunkInputTokens;
        outputTokens += chunkOutputTokens;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public ImportFileType getFileType() {
        return fileType;
    }

    public void setFileType(ImportFileType fileType) {
        this.fileType = fileType;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public void setMediaId(Long mediaId) {
        this.mediaId = mediaId;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public void setStatus(ImportJobStatus status) {
        this.status = status;
    }

    public LlmProvider getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getProcessedChunks() {
        return processedChunks;
    }

    public int getFailedChunks() {
        return failedChunks;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
