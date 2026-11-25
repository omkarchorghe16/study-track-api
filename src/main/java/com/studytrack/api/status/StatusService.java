package com.studytrack.api.status;

import com.studytrack.api.config.AppProperties;
import com.studytrack.api.status.StatusDtos.StatusResponse;
import com.studytrack.api.user.UserEntity;
import com.studytrack.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class StatusService {
    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final String serviceName;

    public StatusService(UserRepository userRepository, AppProperties appProperties,
                         @Value("${spring.application.name}") String serviceName) {
        this.userRepository = userRepository;
        this.appProperties = appProperties;
        this.serviceName = serviceName;
    }

    /** Reports the service identity and the configured user, which also proves the database is reachable. */
    @Transactional(readOnly = true)
    public StatusResponse getStatus() {
        long userId = appProperties.currentUserId();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Configured user " + userId + " does not exist"));
        return new StatusResponse(serviceName, "v1", userId, user.getDisplayName());
    }
}
