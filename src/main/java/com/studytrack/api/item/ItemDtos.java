package com.studytrack.api.item;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ItemDtos {
    private ItemDtos() {
    }

    /**
     * Body of create and replace. HTML fields hold editor output. Tags are trimmed, lower-cased and
     * de-duplicated; unknown tags are created. A missing {@code difficulty} means MEDIUM; a missing
     * {@code sortOrder} puts a new item last, or keeps an existing item's position.
     */
    public record ItemRequest(
            @NotNull Long topicId,
            @NotNull ItemType itemType,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 500_000) String questionHtml,
            @Size(max = 500_000) String answerHtml,
            @Size(max = 500_000) String notesHtml,
            Difficulty difficulty,
            @Size(max = 20) List<@NotBlank @Size(max = 50) String> tags,
            @Min(0) Integer sortOrder) {
    }

    /** One item in full. {@code previousItemId}/{@code nextItemId} are its neighbours in the topic, or null. */
    public record ItemResponse(
            Long id,
            Long topicId,
            Long subjectId,
            ItemType itemType,
            String title,
            String questionHtml,
            String answerHtml,
            String notesHtml,
            Difficulty difficulty,
            ItemSource source,
            List<String> tags,
            int sortOrder,
            Long previousItemId,
            Long nextItemId,
            ItemProgressResponse progress,
            Instant createdAt,
            Instant updatedAt) {
    }

    /**
     * The current user's progress on one item. Items never touched are NOT_STARTED with no reviews.
     * {@code confidence} is 1-5 or absent; {@code nextReviewAt} is absent until the first flashcard review.
     */
    public record ItemProgressResponse(
            ItemStatus status,
            Integer confidence,
            boolean bookmarked,
            int reviewCount,
            Instant lastReviewedAt,
            Instant nextReviewAt) {

        public static final ItemProgressResponse NOT_STARTED =
                new ItemProgressResponse(ItemStatus.NOT_STARTED, null, false, 0, null, null);
    }

    public record ItemSummaryResponse(
            Long id,
            Long topicId,
            ItemType itemType,
            String title,
            Difficulty difficulty,
            List<String> tags,
            int sortOrder,
            boolean hasAnswer,
            ItemStatus status,
            boolean bookmarked,
            Instant updatedAt) {
    }

    /** A page of a topic's items; {@code page} starts at 0. */
    public record ItemPageResponse(
            List<ItemSummaryResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }

    /**
     * Body of POST /items/duplicate-check: the item being written. {@code itemId} is the item being
     * edited, which is not reported as its own duplicate.
     */
    public record DuplicateCheckRequest(
            @NotNull Long topicId,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 500_000) String questionHtml,
            Long itemId) {
    }

    /** An existing item of the same subject with the same question (same content hash). */
    public record DuplicateItemResponse(Long id, String title, Long topicId, String topicName) {
    }
}
