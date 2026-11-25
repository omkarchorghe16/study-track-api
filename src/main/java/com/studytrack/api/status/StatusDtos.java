package com.studytrack.api.status;

public final class StatusDtos {
    private StatusDtos() {
    }

    /**
     * @param service       application name
     * @param apiVersion    REST API version prefix
     * @param currentUserId user every request acts as until login is added
     * @param displayName   that user's display name, read from the database
     */
    public record StatusResponse(String service, String apiVersion, long currentUserId, String displayName) {
    }
}
