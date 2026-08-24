package com.sluice.api.asset.service;

import com.sluice.api.config.MediaSafetyException;
import com.sluice.api.pipeline.MediaResource;
import com.sluice.api.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.InputStream;

/** Verifies that uploaded image bytes are decodable before an asset is marked complete. */
@Component
public class MediaContentVerifier {
    private final StorageService storage;

    public MediaContentVerifier(StorageService storage) {
        this.storage = storage;
    }

    public void verify(String storageUrl, String declaredContentType) {
        if (declaredContentType == null || !declaredContentType.startsWith("image/")) return;
        MediaResource resource = storage.downloadFile(storageUrl);
        if (resource == null) {
            throw new MediaSafetyException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "media_unreadable", "Uploaded media could not be inspected");
        }
        try (InputStream input = resource.getInputStream()) {
            if (ImageIO.read(input) == null) {
                throw new MediaSafetyException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "media_unreadable", "Uploaded image could not be decoded");
            }
        } catch (MediaSafetyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MediaSafetyException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "media_unreadable", "Uploaded media could not be inspected");
        } finally {
            resource.cleanup();
        }
    }
}
