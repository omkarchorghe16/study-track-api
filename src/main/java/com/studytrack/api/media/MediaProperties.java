package com.studytrack.api.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code app.media.*}; defaults and environment variables are in {@code application.yml}.
 *
 * @param storageDir    directory that holds uploaded files
 * @param maxImageBytes largest accepted image, in bytes
 */
@Validated
@ConfigurationProperties("app.media")
public record MediaProperties(@NotBlank String storageDir, @Positive long maxImageBytes) {
}
