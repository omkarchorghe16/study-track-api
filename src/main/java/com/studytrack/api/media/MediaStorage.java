package com.studytrack.api.media;

import java.io.IOException;
import java.io.InputStream;

/** Where uploaded file bytes live. Local disk now; an S3 implementation can replace it later. */
public interface MediaStorage {

    void storeFile(String storageKey, byte[] content) throws IOException;

    InputStream openFile(String storageKey) throws IOException;

    /** Deletes the file; a missing file is not an error. */
    void deleteFile(String storageKey) throws IOException;
}
