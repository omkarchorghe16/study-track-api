package com.studytrack.api.progress;

import com.studytrack.api.item.Difficulty;
import com.studytrack.api.item.ItemDtos.ItemProgressResponse;
import com.studytrack.api.item.ItemStatus;
import com.studytrack.api.item.ItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class ProgressDtos {
    private ProgressDtos() {
    }

    /**
     * Partial update of an item's progress: missing fields stay unchanged. {@code confidence} is 1-5;
     * 0 clears it.
     */
    public record UpdateItemProgressRequest(
            ItemStatus status,
            @Min(0) @Max(5) Integer confidence,
            Boolean bookmarked) {
    }

    public record TopicCompletionResponse(Long topicId, boolean completed, Instant completedAt) {
    }

    public record ReviewRequest(@NotNull Long itemId, @NotNull ReviewResult result) {
    }

    /** The logged review and the item's progress after it ({@code nextReviewAt} set by the scheduler). */
    public record ReviewResponse(Long itemId, ReviewResult result, Instant reviewedAt, long intervalDays,
                                 ItemProgressResponse progress) {
    }

    /** One flashcard. {@code status} is NOT_STARTED and {@code nextReviewAt} absent for new items. */
    public record DueItemResponse(
            Long id,
            Long topicId,
            String topicName,
            ItemType itemType,
            String title,
            String questionHtml,
            String answerHtml,
            String notesHtml,
            Difficulty difficulty,
            ItemStatus status,
            int reviewCount,
            Instant nextReviewAt) {
    }

    /**
     * Flashcards for today. {@code dueCount} counts scheduled reviews due today or earlier and
     * {@code newCount} items never reviewed (not DONE); {@code items} holds at most {@code limit} of them.
     */
    public record DueItemsResponse(Long subjectId, long dueCount, long newCount, List<DueItemResponse> items) {
    }

    /** Opening a subject, topic or item saves it as the place to continue; the item must be in the topic. */
    public record SaveSessionRequest(@NotNull Long subjectId, Long topicId, Long itemId) {
    }

    /** Where to continue. Topic and item are absent when none was opened yet, or they were deleted. */
    public record ResumeResponse(
            Long subjectId,
            String subjectName,
            String subjectIcon,
            Long topicId,
            String topicName,
            Long itemId,
            String itemTitle,
            Instant updatedAt) {
    }
}
