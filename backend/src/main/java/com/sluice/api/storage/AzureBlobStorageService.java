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
        
        com.azure.storage.blob.models.BlobServiceProperties properties = blobServiceClient.getProperties();
        java.util.List<com.azure.storage.blob.models.BlobCorsRule> corsRules = properties.getCors();
        if (corsRules == null) {
            corsRules = new java.util.ArrayList<>();
        }
        
        // Remove existing rule for localhost:3000 if it exists
        corsRules.removeIf(rule -> rule.getAllowedOrigins().contains("http://localhost:3000"));
        
        com.azure.storage.blob.models.BlobCorsRule corsRule = new com.azure.storage.blob.models.BlobCorsRule()
                .setAllowedOrigins("http://localhost:3000")
                .setAllowedMethods("GET,PUT,OPTIONS,POST")
                .setAllowedHeaders("*")
                .setExposedHeaders("*")
                .setMaxAgeInSeconds(3600);
        
        corsRules.add(corsRule);
        properties.setCors(corsRules);
        blobServiceClient.setProperties(properties);
    }

    @Override
    public String uploadFile(MultipartFile file) throws IOException {
        return uploadFile(file.getOriginalFilename(), file.getContentType(), file.getInputStream(), file.getSize());
    }

    @Override
    public String uploadFile(String filename, String contentType, java.io.InputStream inputStream, long size) throws IOException {
        String extension = "";
        if (filename != null && filename.contains(".")) {
            extension = filename.substring(filename.lastIndexOf("."));
        }
        String blobName = UUID.randomUUID().toString() + extension;
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.upload(inputStream, size, true);
        
        com.azure.storage.blob.models.BlobHttpHeaders headers = new com.azure.storage.blob.models.BlobHttpHeaders();
        if (contentType != null) {
            headers.setContentType(contentType);
            blobClient.setHttpHeaders(headers);
        }
        
        return blobClient.getBlobUrl();
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            blobClient.deleteIfExists();
        } catch (Exception e) {
            System.err.println("Failed to delete blob from URL: " + fileUrl + " - " + e.getMessage());
        }
    }

    @Override
    public com.sluice.api.pipeline.MediaResource downloadFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("File URL cannot be null or empty");
        }
        String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        
        java.io.InputStream is = blobClient.openInputStream();
        long size = blobClient.getProperties().getBlobSize();
        
        return new com.sluice.api.pipeline.StreamMediaResource(is, size);
    }

    @Override
    public String generateUploadUrl(String blobName, String contentType) {
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        
        com.azure.storage.blob.sas.BlobSasPermission permission = new com.azure.storage.blob.sas.BlobSasPermission()
                .setWritePermission(true)
                .setCreatePermission(true);
        
        com.azure.storage.blob.sas.BlobServiceSasSignatureValues values = new com.azure.storage.blob.sas.BlobServiceSasSignatureValues(
                java.time.OffsetDateTime.now().plusHours(1),
                permission
        ).setContentType(contentType);
        
        String sasToken = blobClient.generateSas(values);
        return blobClient.getBlobUrl() + "?" + sasToken;
    }

    @Override
    public boolean fileExists(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return false;
        }
        String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        return blobClient.exists();
    }

    @Override
    public long getFileSize(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("File URL cannot be null or empty");
        }
        String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        if (blobClient.exists()) {
            return blobClient.getProperties().getBlobSize();
        }
        return -1;
    }
}
