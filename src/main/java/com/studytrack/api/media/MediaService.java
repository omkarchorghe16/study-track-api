package com.studytrack.api.media;

import com.studytrack.api.media.MediaDtos.MediaContent;
import com.studytrack.api.media.MediaDtos.MediaResponse;
import com.studytrack.api.user.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {
    public static final String MEDIA_URL_PREFIX = "/api/v1/media/";

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_ALT_TEXT_LENGTH = 500;

    private final MediaFileRepository mediaFileRepository;
    private final MediaStorage mediaStorage;
    private final MediaProperties mediaProperties;
    private final CurrentUser currentUser;

    public MediaService(MediaFileRepository mediaFileRepository, MediaStorage mediaStorage,
                        MediaProperties mediaProperties, CurrentUser currentUser) {
        this.mediaFileRepository = mediaFileRepository;
        this.mediaStorage = mediaStorage;
        this.mediaProperties = mediaProperties;
        this.currentUser = currentUser;
    }

    /**
     * Stores an image for the current user. The format is detected from the bytes (PNG, JPEG, GIF,
     * WebP); anything else is rejected with 415, and files over {@code app.media.max-image-bytes} with 413.
     */
    @Transactional
    public MediaResponse uploadImage(MultipartFile file, String altText) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (file.getSize() > mediaProperties.maxImageBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Images can be at most " + mediaProperties.maxImageBytes() / 1024 + " KB");
        }
        byte[] content = readBytes(file);
        ImageType imageType = ImageType.detectImageType(content)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Only PNG, JPEG, GIF and WebP images are accepted"));

        String storageKey = newStorageKey(imageType.extension());
        MediaFileEntity media = new MediaFileEntity(currentUser.getUserId(), storageKey);
        media.setOriginalName(truncate(originalName(file, imageType), MAX_NAME_LENGTH));
        media.setContentType(imageType.contentType());
        media.setSizeBytes(content.length);
        media.setAltText(truncate(blankToNull(altText), MAX_ALT_TEXT_LENGTH));
        readDimensions(content, media);

        storeFile(storageKey, content);
        deleteFileIfRolledBack(storageKey);
        return toResponse(mediaFileRepository.save(media));
    }

    /**
     * Stores an image found inside an imported document for {@code userId} (the import runs in the
     * background, outside a request). Returns empty when the bytes are not a supported image or are
     * larger than {@code app.media.max-image-bytes}; the import then leaves that image out.
     */
    @Transactional
    public Optional<Long> storeImportedImage(long userId, byte[] content, String altText) {
        if (content.length == 0 || content.length > mediaProperties.maxImageBytes()) {
            return Optional.empty();
        }
        Optional<ImageType> imageType = ImageType.detectImageType(content);
        if (imageType.isEmpty()) {
            return Optional.empty();
        }
        String storageKey = newStorageKey(imageType.get().extension());
        MediaFileEntity media = new MediaFileEntity(userId, storageKey);
        media.setOriginalName("imported-image." + imageType.get().extension());
        media.setContentType(imageType.get().contentType());
        media.setSizeBytes(content.length);
        media.setAltText(truncate(blankToNull(altText), MAX_ALT_TEXT_LENGTH));
        readDimensions(content, media);
        storeFile(storageKey, content);
        deleteFileIfRolledBack(storageKey);
        return Optional.of(mediaFileRepository.save(media).getId());
    }

    /**
     * Stores an uploaded document (the original file of an import) for the current user. The caller has
     * checked its type and size; {@code extension} names the stored file.
     */
    @Transactional
    public Long storeDocument(byte[] content, String originalName, String contentType, String extension) {
        String storageKey = newStorageKey(extension);
        MediaFileEntity media = new MediaFileEntity(currentUser.getUserId(), storageKey);
        media.setOriginalName(truncate(originalName, MAX_NAME_LENGTH));
        media.setContentType(contentType);
        media.setSizeBytes(content.length);
        storeFile(storageKey, content);
        deleteFileIfRolledBack(storageKey);
        return mediaFileRepository.save(media).getId();
    }

    /** Reads a stored file of {@code userId} into memory (imports read their document back this way). */
    @Transactional(readOnly = true)
    public byte[] readMediaBytes(long userId, Long mediaId) {
        MediaFileEntity media = mediaFileRepository.findByIdAndUserId(mediaId, userId)
                .orElseThrow(() -> new NoSuchElementException("Media " + mediaId + " not found"));
        try (InputStream input = mediaStorage.openFile(media.getStorageKey())) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("The stored file could not be read", exception);
        }
    }

    @Transactional(readOnly = true)
    public MediaResponse getMedia(Long mediaId) {
        return toResponse(findOwnedMedia(mediaId));
    }

    /** Opens the stored file; the caller (the HTTP response) closes the stream. */
    @Transactional(readOnly = true)
    public MediaContent openMediaContent(Long mediaId) {
        MediaFileEntity media = findOwnedMedia(mediaId);
        try {
            return new MediaContent(media.getContentType(), media.getSizeBytes(), media.getOriginalName(),
                    mediaStorage.openFile(media.getStorageKey()));
        } catch (IOException exception) {
            log.warn("Stored file missing or unreadable: mediaId={}", mediaId);
            throw new NoSuchElementException("Media " + mediaId + " not found");
        }
    }

    @Transactional
    public void deleteMedia(Long mediaId) {
        MediaFileEntity media = findOwnedMedia(mediaId);
        mediaFileRepository.delete(media);
        deleteFilesAfterCommit(List.of(media.getStorageKey()));
    }

    /**
     * Makes {@code mediaIds} (the images an item's HTML references) the item's images: the user's
     * unlinked images among them are linked, and images the item no longer uses are unlinked.
     * Ids of other users' images, or of images used by another item, are ignored.
     */
    @Transactional
    public void linkItemMedia(Long itemId, Set<Long> mediaIds) {
        if (!mediaIds.isEmpty()) {
            mediaFileRepository.linkToItem(itemId, currentUser.getUserId(), mediaIds);
        }
        Collection<Long> keptMediaIds = mediaIds.isEmpty() ? List.of(-1L) : mediaIds;
        mediaFileRepository.unlinkFromItem(itemId, keptMediaIds);
    }

    /** Deletes the images linked to an item, rows now and files once the transaction commits. */
    @Transactional
    public void deleteItemMedia(Long itemId) {
        List<MediaFileEntity> mediaFiles = mediaFileRepository.findByItemId(itemId);
        if (mediaFiles.isEmpty()) {
            return;
        }
        mediaFileRepository.deleteAll(mediaFiles);
        deleteFilesAfterCommit(mediaFiles.stream().map(MediaFileEntity::getStorageKey).toList());
    }

    /**
     * Deletes images that nothing uses and that were created before {@code createdBefore} (all users),
     * rows now and files once the transaction commits. Returns how many were deleted.
     */
    @Transactional
    public int deleteUnusedMedia(Instant createdBefore) {
        List<MediaFileEntity> unused = mediaFileRepository.findUnusedMedia(createdBefore);
        if (unused.isEmpty()) {
            return 0;
        }
        mediaFileRepository.deleteAll(unused);
        deleteFilesAfterCommit(unused.stream().map(MediaFileEntity::getStorageKey).toList());
        return unused.size();
    }

    private MediaFileEntity findOwnedMedia(Long mediaId) {
        return mediaFileRepository.findByIdAndUserId(mediaId, currentUser.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Media " + mediaId + " not found"));
    }

    private void storeFile(String storageKey, byte[] content) {
        try {
            mediaStorage.storeFile(storageKey, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store the uploaded file", exception);
        }
    }

    /** If saving the row fails after the file was written, remove the file again. */
    private void deleteFileIfRolledBack(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteStoredFile(storageKey);
                }
            }
        });
    }

    private void deleteFilesAfterCommit(List<String> storageKeys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageKeys.forEach(this::deleteStoredFile);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageKeys.forEach(MediaService.this::deleteStoredFile);
            }
        });
    }

    private void deleteStoredFile(String storageKey) {
        try {
            mediaStorage.deleteFile(storageKey);
        } catch (IOException exception) {
            log.warn("Could not delete stored file: storageKey={} exceptionType={}", storageKey,
                    exception.getClass().getSimpleName());
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the uploaded file", exception);
        }
    }

    /** Reads width and height from the image header without decoding the pixels; unknown formats leave them null. */
    private static void readDimensions(byte[] content, MediaFileEntity media) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                media.setWidth(reader.getWidth(0));
                media.setHeight(reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            log.debug("Image dimensions not readable: exceptionType={}", exception.getClass().getSimpleName());
        }
    }

    private static String newStorageKey(String extension) {
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        return "%d/%02d/%s.%s".formatted(month.getYear(), month.getMonthValue(), UUID.randomUUID(), extension);
    }

    private static String originalName(MultipartFile file, ImageType imageType) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "image." + imageType.extension();
        }
        // Keep only the last path segment and drop control characters.
        String baseName = name.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        return baseName.replaceAll("\\p{Cntrl}", "");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private MediaResponse toResponse(MediaFileEntity media) {
        return new MediaResponse(media.getId(), MEDIA_URL_PREFIX + media.getId(), media.getOriginalName(),
                media.getContentType(), media.getSizeBytes(), media.getWidth(), media.getHeight(),
                media.getAltText(), media.getItemId(), media.getCreatedAt());
    }
}
