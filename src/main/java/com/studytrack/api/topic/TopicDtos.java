package com.studytrack.api.topic;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TopicDtos {
    private TopicDtos() {
    }

    /** A missing {@code sortOrder} puts the topic after its siblings. A null {@code parentId} makes it top-level. */
    public record CreateTopicRequest(
            @NotNull Long subjectId,
            Long parentId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200_000) String descriptionHtml,
            @Min(0) Integer sortOrder) {
    }

    /** Replaces name and description. A missing {@code sortOrder} keeps the current position; use move to re-parent. */
    public record ReplaceTopicRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200_000) String descriptionHtml,
            @Min(0) Integer sortOrder) {
    }

    /** A null {@code parentId} moves the topic to the top level. */
    public record MoveTopicRequest(Long parentId, @NotNull @Min(0) Integer sortOrder) {
    }

    public record TopicResponse(
            Long id,
            Long subjectId,
            Long parentId,
            String name,
            String descriptionHtml,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {
    }

    /**
     * One node of the topic tree. {@code itemCount} counts the topic's own items and
     * {@code totalItemCount} adds those of all its subtopics. Progress is added in phase 4.
     */
    public record TopicNodeResponse(
            Long id,
            Long parentId,
            String name,
            String descriptionHtml,
            int sortOrder,
            long itemCount,
            long totalItemCount,
            List<TopicNodeResponse> children) {
    }
}
