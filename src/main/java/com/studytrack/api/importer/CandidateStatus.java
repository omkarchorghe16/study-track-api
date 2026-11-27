package com.studytrack.api.importer;

/**
 * State of a question found by an import. Imports save drafts as questions straight away: APPROVED ones became
 * questions, DUPLICATE ones were not saved because the subject already had the same question. PENDING and EDITED
 * are left only by imports from before V13 (or when saving failed) and can still be saved; REJECTED were dropped.
 */
public enum CandidateStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EDITED,
    DUPLICATE;

    public boolean isOpen() {
        return this == PENDING || this == EDITED;
    }
}
