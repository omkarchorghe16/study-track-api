package com.studytrack.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings under {@code app.*}. Defaults and environment variable names are in
 * {@code application.yml}.
 *
 * @param currentUserId user every request acts as until login is added
 * @param cors          browser origins allowed to call {@code /api/**}
 * @param timeZone      zone that decides what "today" means for due reviews, streaks and the activity
 *                      chart, default {@code America/Chicago}; blank means the server's zone
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotNull @Positive Long currentUserId, @Valid @NotNull Cors cors, String timeZone) {

    /** @param allowedOrigin origin of the Angular app, for example {@code http://localhost:4201} */
    public record Cors(@NotBlank String allowedOrigin) {
    }
}
