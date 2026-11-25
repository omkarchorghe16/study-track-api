package com.studytrack.api.importer;

/** UPLOADED → PARSING → EXTRACTING → READY_FOR_REVIEW → COMPLETED (every candidate handled); FAILED on error. */
public enum ImportJobStatus {
    UPLOADED,
    PARSING,
    EXTRACTING,
    READY_FOR_REVIEW,
    COMPLETED,
    FAILED;

    public boolean isRunning() {
        return this == UPLOADED || this == PARSING || this == EXTRACTING;
    }
}
