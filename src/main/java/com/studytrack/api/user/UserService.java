package com.studytrack.api.user;

import com.studytrack.api.user.UserDtos.CurrentUserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserService(UserRepository userRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        long userId = currentUser.getUserId();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User " + userId + " not found"));
        return new CurrentUserResponse(user.getId(), user.getDisplayName(), user.isAdmin());
    }
}
