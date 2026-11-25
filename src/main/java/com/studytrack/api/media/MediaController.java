package com.studytrack.api.media;

import com.studytrack.api.media.MediaDtos.MediaContent;
import com.studytrack.api.media.MediaDtos.MediaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "Images used in item HTML")
public class MediaController {
    private static final Duration CACHE_DURATION = Duration.ofDays(365);

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image", description = "PNG, JPEG, GIF or WebP (detected from the bytes). Returns the URL to put in item HTML.")
    @ApiResponse(responseCode = "201", description = "Stored")
    @ApiResponse(responseCode = "413", description = "Larger than app.media.max-image-bytes")
    @ApiResponse(responseCode = "415", description = "Not a supported image format")
    public ResponseEntity<MediaResponse> uploadImage(@RequestPart("file") MultipartFile file,
                                                     @RequestParam(required = false) String altText) {
        MediaResponse uploaded = mediaService.uploadImage(file, altText);
        return ResponseEntity.created(URI.create(uploaded.url())).body(uploaded);
    }

    /** A stored file never changes (a new upload gets a new id), so browsers may cache it for a year. */
    @GetMapping("/{mediaId}")
    @Operation(summary = "Download an image", description = "Streams the file with its content type and long-lived private caching.")
    @ApiResponse(responseCode = "404", description = "No such image for the current user")
    public ResponseEntity<InputStreamResource> getMediaContent(@PathVariable Long mediaId) {
        MediaContent media = mediaService.openMediaContent(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .contentLength(media.sizeBytes())
                .cacheControl(CacheControl.maxAge(CACHE_DURATION).cachePrivate().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(media.originalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(media.content()));
    }

    @GetMapping("/{mediaId}/info")
    @Operation(summary = "Get an image's details", description = "Name, type, size, dimensions and the item it is used in.")
    public MediaResponse getMedia(@PathVariable Long mediaId) {
        return mediaService.getMedia(mediaId);
    }

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "Delete an image", description = "Items that still reference it will show a broken image.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> deleteMedia(@PathVariable Long mediaId) {
        mediaService.deleteMedia(mediaId);
        return ResponseEntity.noContent().build();
    }
}
