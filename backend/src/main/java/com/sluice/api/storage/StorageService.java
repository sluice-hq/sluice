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
     * Uploads a file from an input stream to the underlying storage mechanism.
     * @param filename The original filename.
     * @param contentType The MIME type.
     * @param inputStream The input stream.
     * @param size The size of the file.
     * @return The URL or path to the stored file.
     * @throws IOException If the upload fails.
     */
    String uploadFile(String filename, String contentType, java.io.InputStream inputStream, long size) throws IOException;

    /**
     * Deletes a file from the underlying storage mechanism.
     * @param fileUrl The URL or path of the file to delete.
     */
    void deleteFile(String fileUrl);

    /**
     * Downloads a file from the underlying storage mechanism.
     * @param fileUrl The URL or path of the file to download.
     * @return The file contents as a MediaResource.
     */
    com.sluice.api.pipeline.MediaResource downloadFile(String fileUrl);

    /**
     * Generates a direct upload URL (SAS) for a file.
     * @param blobName The name of the blob to create.
     * @param contentType The expected content type.
     * @return The upload URL.
     */
    String generateUploadUrl(String blobName, String contentType);

    /**
     * Checks if a file exists.
     * @param fileUrl The URL of the file.
     * @return true if the file exists.
     */
    boolean fileExists(String fileUrl);

    /**
     * Gets the size of a file.
     * @param fileUrl The URL of the file.
     * @return The size in bytes.
     */
    long getFileSize(String fileUrl);
}
