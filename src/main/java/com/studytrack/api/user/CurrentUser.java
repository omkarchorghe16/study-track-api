package com.studytrack.api.user;

import com.studytrack.api.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The user the current request acts for. Until login is added this is always
 * {@code app.current-user-id}; services ask here so the switch to authenticated users is one change.
 */
@Component
public class CurrentUser {
    private final AppProperties appProperties;
    private final UserRepository userRepository;

    public CurrentUser(AppProperties appProperties, UserRepository userRepository) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
    }

    public long getUserId() {
        return appProperties.currentUserId();
    }

    public boolean isAdmin() {
        return userRepository.findById(getUserId()).map(UserEntity::isAdmin).orElse(false);
    }

    /** Throws 403 unless the current user is an admin. */
    public void requireAdmin() {
        requireAdmin("change the catalog");
    }

    /** Throws 403 "Only an admin can {action}" unless the current user is an admin. */
    public void requireAdmin(String action) {
        if (!isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an admin can " + action);
        }
    }
}
