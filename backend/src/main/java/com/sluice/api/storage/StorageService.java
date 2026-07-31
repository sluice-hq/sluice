package com.sluice.api.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface StorageService {
    /**
     * Uploads a file to the underlying storage mechanism.
     * @param file The file to upload.
     * @return The URL or path to the stored file.
     * @throws IOException If the upload fails.
     */
    String uploadFile(MultipartFile file) throws IOException;

    /**
     * Deletes a file from the underlying storage mechanism.
     * @param fileUrl The URL or path of the file to delete.
     */
    void deleteFile(String fileUrl);
}
