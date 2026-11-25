package com.studytrack.api.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Deletes unused images on the {@code app.media.cleanup-cron} schedule, after {@code cleanup-grace}. */
@Component
public class MediaCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    private final MediaService mediaService;
    private final MediaProperties mediaProperties;
    private final Clock clock;

    public MediaCleanupJob(MediaService mediaService, MediaProperties mediaProperties, Clock clock) {
        this.mediaService = mediaService;
        this.mediaProperties = mediaProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.media.cleanup-cron}")
    public void deleteUnusedMedia() {
        Instant createdBefore = clock.instant().minus(mediaProperties.cleanupGrace());
        int deletedCount = mediaService.deleteUnusedMedia(createdBefore);
        log.info("Deleted unused media: deletedCount={} createdBefore={}", deletedCount, createdBefore);
    }
}
