package com.studytrack.api.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under {@code app.media.*}; defaults and environment variables are in {@code application.yml}.
 *
 * @param storageDir    directory that holds uploaded files
 * @param maxImageBytes largest accepted image, in bytes
 * @param cleanupCron   when unused images are deleted (Spring cron; "-" turns the cleanup off)
 * @param cleanupGrace  how old an unused image must be before the cleanup deletes it
 */
@Validated
@ConfigurationProperties("app.media")
public record MediaProperties(@NotBlank String storageDir, @Positive long maxImageBytes, @NotBlank String cleanupCron,
                              @NotNull Duration cleanupGrace) {
}
