package com.studytrack.api.media;

import java.io.InputStream;
import java.time.Instant;

public final class MediaDtos {
    private MediaDtos() {
    }

    /** An uploaded image. {@code url} is the path to use in HTML: {@code <img src="{url}">}. */
    public record MediaResponse(
            Long id,
            String url,
            String originalName,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height,
            String altText,
            Long itemId,
            Instant createdAt) {
    }

    /** The bytes of a stored file with what the HTTP response needs to describe them. */
    public record MediaContent(String contentType, long sizeBytes, String originalName, InputStream content) {
    }
}
