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
            Instant createdAt,
            Instant updatedAt) {
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
}
