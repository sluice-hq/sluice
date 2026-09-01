package com.sluice.api.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.sluice.api.observability.SluiceMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;

@Service
@Profile("!test")
public class AzureBlobStorageService implements StorageService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AzureBlobStorageService.class);

    private final BlobServiceClient blobServiceClient;
    private final String containerName;
    private final long uploadSasExpiryHours;
    private final long downloadSasExpiryHours;
    private final boolean configureCors;
    private final String corsAllowedOrigins;
    private final String publicBaseUrl;
    private final SluiceMetrics metrics;
    private BlobContainerClient containerClient;

    public AzureBlobStorageService(
            BlobServiceClient blobServiceClient,
            @Value("${azure.storage.container-name:assets}") String containerName,
            @Value("${azure.storage.sas.upload-expiry-hours:1}") long uploadSasExpiryHours,
            @Value("${azure.storage.sas.download-expiry-hours:24}") long downloadSasExpiryHours,
            @Value("${azure.storage.configure-cors:false}") boolean configureCors,
            @Value("${azure.storage.cors.allowed-origins:}") String corsAllowedOrigins,
            @Value("${azure.storage.public-base-url:}") String publicBaseUrl,
            SluiceMetrics metrics) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = containerName;
        this.uploadSasExpiryHours = uploadSasExpiryHours;
        this.downloadSasExpiryHours = downloadSasExpiryHours;
        this.configureCors = configureCors;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        
        if (!configureCors || corsAllowedOrigins.isBlank()) {
            return;
        }

        com.azure.storage.blob.models.BlobServiceProperties properties = blobServiceClient.getProperties();
        java.util.List<com.azure.storage.blob.models.BlobCorsRule> corsRules = properties.getCors();
        if (corsRules == null) {
            corsRules = new java.util.ArrayList<>();
        }
        
        corsRules.removeIf(rule -> rule.getAllowedOrigins().equals(corsAllowedOrigins));
        
        com.azure.storage.blob.models.BlobCorsRule corsRule = new com.azure.storage.blob.models.BlobCorsRule()
                .setAllowedOrigins(corsAllowedOrigins)
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
        return uploadFileAt(blobName, contentType, inputStream, size);
    }

    @Override
    public String uploadFileAt(String objectName, String contentType, java.io.InputStream inputStream, long size)
            throws IOException {
        return observe("upload", () -> {
            BlobClient blobClient = containerClient.getBlobClient(objectName);
            blobClient.upload(inputStream, size, true);

            com.azure.storage.blob.models.BlobHttpHeaders headers = new com.azure.storage.blob.models.BlobHttpHeaders();
            if (contentType != null) {
                headers.setContentType(contentType);
                blobClient.setHttpHeaders(headers);
            }

            return blobClient.getBlobUrl();
        });
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            observe("delete", () -> {
                String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
                String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
                containerClient.getBlobClient(blobName).deleteIfExists();
                return null;
            });
        } catch (Exception e) {
            log.error("blob_delete_failed storageUrl={}", fileUrl, e);
        }
    }

    @Override
    public com.sluice.api.pipeline.MediaResource downloadFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("File URL cannot be null or empty");
        }
        return observe("download", () -> {
            String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            java.nio.file.Path tempFile = null;
            try {
                tempFile = java.nio.file.Files.createTempFile("sluice-input-", ".bin");
                blobClient.downloadToFile(tempFile.toString(), true);
                String contentType = blobClient.getProperties().getContentType();
                return new com.sluice.api.pipeline.FileMediaResource(tempFile.toFile(), contentType);
            } catch (Exception e) {
                if (tempFile != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(tempFile);
                    } catch (java.io.IOException ignored) {
                        // Preserve the original download failure.
                    }
                }
                throw new RuntimeException("Failed to download blob for processing", e);
            }
        });
    }

    @Override
    public String generateUploadUrl(String blobName, String contentType) {
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        
        com.azure.storage.blob.sas.BlobSasPermission permission = new com.azure.storage.blob.sas.BlobSasPermission()
                .setWritePermission(true)
                .setCreatePermission(true);
        
        com.azure.storage.blob.sas.BlobServiceSasSignatureValues values = new com.azure.storage.blob.sas.BlobServiceSasSignatureValues(
                java.time.OffsetDateTime.now().plusHours(uploadSasExpiryHours),
                permission
        ).setContentType(contentType);
        
        String sasToken = blobClient.generateSas(values);
        return expose(blobClient.getBlobUrl()) + "?" + sasToken;
    }

    @Override
    public String generateDownloadUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("File URL cannot be null or empty");
        }
        String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        
        com.azure.storage.blob.sas.BlobSasPermission permission = new com.azure.storage.blob.sas.BlobSasPermission()
                .setReadPermission(true);
        
        com.azure.storage.blob.sas.BlobServiceSasSignatureValues values = new com.azure.storage.blob.sas.BlobServiceSasSignatureValues(
                java.time.OffsetDateTime.now().plusHours(downloadSasExpiryHours),
                permission
        );
        
        String sasToken = blobClient.generateSas(values);
        return expose(blobClient.getBlobUrl()) + "?" + sasToken;
    }

    @Override
    public boolean fileExists(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return false;
        }
        return observe("exists", () -> {
            String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
            return containerClient.getBlobClient(blobName).exists();
        });
    }

    @Override
    public long getFileSize(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("File URL cannot be null or empty");
        }
        return observe("properties", () -> {
            String encodedName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            String blobName = java.net.URLDecoder.decode(encodedName, java.nio.charset.StandardCharsets.UTF_8);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            return blobClient.exists() ? blobClient.getProperties().getBlobSize() : -1;
        });
    }

    private <T> T observe(String operation, java.util.function.Supplier<T> action) {
        long started = System.nanoTime();
        String outcome = "success";
        try {
            return action.get();
        } catch (RuntimeException exception) {
            outcome = "failure";
            throw exception;
        } finally {
            metrics.storage(operation, outcome, System.nanoTime() - started);
        }
    }

    private String expose(String blobUrl) {
        if (publicBaseUrl.isBlank()) {
            return blobUrl;
        }
        return replaceAccountBase(blobServiceClient.getAccountUrl(), publicBaseUrl, blobUrl);
    }

    static String replaceAccountBase(String internalBaseUrl, String publicBaseUrl, String blobUrl) {
        String internalBase = stripTrailingSlash(internalBaseUrl);
        String publicBase = stripTrailingSlash(publicBaseUrl);
        if (internalBase.isBlank() || publicBase.isBlank() || !blobUrl.startsWith(internalBase + "/")) {
            throw new IllegalArgumentException("Blob URL does not belong to the configured storage account endpoint");
        }
        return publicBase + blobUrl.substring(internalBase.length());
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
