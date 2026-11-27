package com.studytrack.api.explanation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings under {@code app.explanation.*} for the admin's one-off pre-fill of the catalog's explanations.
 *
 * @param prefillPause          wait after each question, to stay within a free API key's requests per minute
 * @param prefillFailurePause   wait after a failed question (usually a rate limit) before the next one
 * @param maxConsecutiveFailures the run stops after this many failures in a row; saved explanations are kept
 */
@ConfigurationProperties("app.explanation")
public record ExplanationProperties(
        @DefaultValue("PT4S") Duration prefillPause,
        @DefaultValue("PT30S") Duration prefillFailurePause,
        @DefaultValue("5") int maxConsecutiveFailures) {
}
