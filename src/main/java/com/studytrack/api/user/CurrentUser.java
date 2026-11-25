package com.studytrack.api.user;

import com.studytrack.api.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * The user the current request acts for. Until login is added this is always
 * {@code app.current-user-id}; services ask here so the switch to authenticated users is one change.
 */
@Component
public class CurrentUser {
    private final AppProperties appProperties;

    public CurrentUser(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public long getUserId() {
        return appProperties.currentUserId();
    }
}
