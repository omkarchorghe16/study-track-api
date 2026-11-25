package com.studytrack.api.importer;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code app.import.*}; defaults and environment variables are in {@code application.yml}.
 *
 * @param maxFileBytes  largest accepted document
 * @param workerThreads background threads that parse documents and call the LLM
 */
@Validated
@ConfigurationProperties("app.import")
public record ImportProperties(@Positive long maxFileBytes, @Positive int workerThreads) {
}
