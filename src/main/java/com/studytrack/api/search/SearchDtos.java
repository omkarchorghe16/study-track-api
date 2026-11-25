package com.studytrack.api.search;

import com.studytrack.api.item.Difficulty;
import com.studytrack.api.item.ItemStatus;
import com.studytrack.api.item.ItemType;

import java.util.List;

public final class SearchDtos {
    private SearchDtos() {
    }

    /**
     * One part of a result snippet. {@code match} is true for words that match the query, so the client
     * can highlight them without rendering HTML.
     */
    public record SnippetPart(String text, boolean match) {
    }

    /** An item that matches the query, with where it lives and the current user's status. */
    public record SearchResultResponse(
            Long itemId,
            String title,
            ItemType itemType,
            Difficulty difficulty,
            ItemStatus status,
            Long subjectId,
            String subjectName,
            Long topicId,
            String topicName,
            List<SnippetPart> snippet) {
    }

    /** A page of search results, best match first; {@code page} starts at 0. */
    public record SearchPageResponse(
            String query,
            List<SearchResultResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }
}
