package com.studytrack.api.importer;

/** PENDING and EDITED candidates still wait for review; APPROVED ones became items, REJECTED ones were dropped. */
public enum CandidateStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EDITED;

    public boolean isOpen() {
        return this == PENDING || this == EDITED;
    }
}
