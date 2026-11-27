package com.studytrack.api.importer;

/**
 * Published by {@link ImportProcessingService} when an import has finished finding questions (also when it
 * failed part-way); {@link ImportService} then saves its drafts as questions. An event keeps the two services
 * from depending on each other.
 */
public record ImportDraftsReadyEvent(Long jobId) {
}
