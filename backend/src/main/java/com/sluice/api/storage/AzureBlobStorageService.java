package com.sluice.api.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;

@Service
public class AzureBlobStorageService implements StorageService {

    private final BlobServiceClient blobServiceClient;
    private final String containerName;
    private BlobContainerClient containerClient;

    public AzureBlobStorageService(
            BlobServiceClient blobServiceClient,
            @Value("${azure.storage.container-name:assets}") String containerName) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = containerName;
    }

    @PostConstruct
    public void init() {
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
    }

    @Override
    public String uploadFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String blobName = UUID.randomUUID().toString() + extension;
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.upload(file.getInputStream(), file.getSize(), true);
        return blobClient.getBlobUrl();
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            String blobName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            blobClient.deleteIfExists();
        } catch (Exception e) {
            System.err.println("Failed to delete blob from URL: " + fileUrl + " - " + e.getMessage());
        }
    }

    @Override
    public byte[] downloadFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("File URL cannot be null or empty");
        }
        String blobName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        return blobClient.downloadContent().toBytes();
    }
}
