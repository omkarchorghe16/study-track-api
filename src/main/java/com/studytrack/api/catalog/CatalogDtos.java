package com.studytrack.api.catalog;

import java.util.List;

/** Transport records of the catalog feature. */
public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record CatalogTrackResponse(Long id, String name, String description, long subjectCount) {
    }

    /** @param copiedSubjectId id of the user's own subject when the user already added this one, else null */
    public record CatalogSubjectResponse(Long id, Long trackId, String name, String description, String icon,
                                         long topicCount, Long copiedSubjectId) {
    }

    public record CatalogTopicNodeResponse(Long id, String name, List<CatalogTopicNodeResponse> children) {
    }

    /**
     * @param rowsRead     rows with data (not counting the header and blank lines)
     * @param rejectedRows why rows were skipped, one line each, at most the first 20
     */
    public record CatalogImportResponse(int rowsRead, int tracksAdded, int subjectsAdded, int topicsAdded,
                                        int rejectedRowCount, List<String> rejectedRows) {
    }
}
