package com.studytrack.api.user;

/** Transport records of the user feature. */
public final class UserDtos {
    private UserDtos() {
    }

    /** @param admin true when the user may change the shared catalog */
    public record CurrentUserResponse(Long id, String displayName, boolean admin) {
    }
}
