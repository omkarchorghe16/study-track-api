package com.studytrack.api.subject;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SubjectDtos {
    private SubjectDtos() {
    }

    /**
     * Body of create and replace. A missing {@code sortOrder} on create puts the subject last; on
     * replace it keeps the current position. A missing {@code archived} means false.
     */
    public record SubjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @Size(max = 16) String icon,
            @Min(0) Integer sortOrder,
            Boolean archived) {
    }

    /**
     * A subject with the current user's progress. {@code percentDone} is DONE items / all items, rounded
     * down (0 when there are no items); {@code completedTopicCount} counts topics marked complete.
     */
    public record SubjectResponse(
            Long id,
            String name,
            String description,
            String icon,
            int sortOrder,
            boolean archived,
            long topicCount,
            long completedTopicCount,
            long itemCount,
            long doneItemCount,
            long learningItemCount,
            int percentDone,
            Instant createdAt,
            Instant updatedAt) {
    }
}
