package com.studytrack.api.importer;

import com.studytrack.api.item.Difficulty;
import com.studytrack.api.item.ItemStatus;
import com.studytrack.api.item.ItemType;
import com.studytrack.api.llm.LlmProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ImportDtos {
    private ImportDtos() {
    }

    /**
     * An import and its progress. {@code percentComplete} is processed chunks over total chunks (0 while
     * parsing). Token counts are the LLM usage so far. {@code errorMessage} is set when FAILED.
     */
    public record ImportJobResponse(
            Long id,
            Long subjectId,
            String fileName,
            ImportFileType fileType,
            ImportJobStatus status,
            LlmProvider llmProvider,
            String llmModel,
            int totalChunks,
            int processedChunks,
            int failedChunks,
            int percentComplete,
            long inputTokens,
            long outputTokens,
            String errorMessage,
            CandidateCounts candidates,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** {@code open} counts PENDING and EDITED candidates, which still wait for review. */
    /** {@code approved}: saved as questions; {@code duplicates}: not saved because the subject already had them. */
    public record CandidateCounts(long total, long open, long approved, long rejected, long duplicates) {
        public static final CandidateCounts NONE = new CandidateCounts(0, 0, 0, 0, 0);
    }

    /**
     * A candidate for review. HTML is sanitised; {@code imageIds} are the media ids it shows.
     * {@code duplicateItemId} is an existing item of the subject with the same question;
     * {@code duplicateOfCandidateId} an earlier open candidate of this import with the same question.
     * {@code confidence} is the LLM's own 0-1 estimate. {@code questionStatus} is the current user's status of the
     * saved question ({@code createdItemId}); DONE means the user ticked it as completed.
     */
    public record CandidateResponse(
            Long id,
            int chunkIndex,
            CandidateStatus status,
            ItemType itemType,
            String title,
            String questionHtml,
            String answerHtml,
            String notesHtml,
            Difficulty difficulty,
            List<String> tags,
            List<Long> imageIds,
            Double confidence,
            String suggestedTopicPath,
            Long targetTopicId,
            Long createdItemId,
            Long duplicateItemId,
            Long duplicateOfCandidateId,
            ItemStatus questionStatus) {
    }

    /**
     * Body of PUT /imports/{id}/candidates/{candidateId}: replaces the editable fields and marks the
     * candidate EDITED. {@code targetTopicId} (a topic of the import's subject) wins over
     * {@code topicPath} ("Spring Boot > Security", created on approval if missing).
     */
    public record CandidateRequest(
            @NotNull ItemType itemType,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 500_000) String questionHtml,
            @Size(max = 500_000) String answerHtml,
            @Size(max = 500_000) String notesHtml,
            Difficulty difficulty,
            @Size(max = 20) List<@NotBlank @Size(max = 50) String> tags,
            @Size(max = 500) String topicPath,
            Long targetTopicId) {
    }

    /** Candidates to approve or reject: the listed ids, or every open candidate when {@code all} is true. */
    public record CandidateSelectionRequest(@Size(max = 1000) List<@NotNull Long> candidateIds, boolean all) {
    }

    public record ApproveResponse(
            int approvedCount,
            int createdTopicCount,
            List<Long> createdItemIds,
            ImportJobResponse job) {
    }

    public record RejectResponse(int rejectedCount, ImportJobResponse job) {
    }
}
