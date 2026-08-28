package com.sluice.api.asset.service;

import com.sluice.api.config.MediaSafetyException;
import com.sluice.api.pipeline.MediaResource;
import com.sluice.api.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Verifies uploaded bytes against the declared globally-supported media type. */
@Component
public class MediaContentVerifier {
    private static final int HEADER_LIMIT = 4096;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif");
    private final StorageService storage;

    public MediaContentVerifier(StorageService storage) {
        this.storage = storage;
    }

    public void verify(String storageUrl, String declaredContentType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            throw unsupported("Uploaded media type cannot be verified");
        }
        String contentType = declaredContentType.toLowerCase(java.util.Locale.ROOT);
        if (!IMAGE_TYPES.contains(contentType) && !"application/pdf".equals(contentType)
                && !"video/mp4".equals(contentType)) {
            throw unsupported("Uploaded media type has no content verifier");
        }
        MediaResource resource = storage.downloadFile(storageUrl);
        if (resource == null) {
            throw unsupported("Uploaded media could not be inspected");
        }
        try (InputStream input = resource.getInputStream()) {
            if (IMAGE_TYPES.contains(contentType)) {
                if (ImageIO.read(input) == null) throw unsupported("Uploaded image could not be decoded");
            } else {
                byte[] header = input.readNBytes(HEADER_LIMIT);
                boolean valid = "application/pdf".equals(contentType) ? isPdf(header) : isMp4(header);
                if (!valid) throw unsupported("Uploaded bytes do not match the declared media type");
            }
        } catch (MediaSafetyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unsupported("Uploaded media could not be inspected");
        } finally {
            resource.cleanup();
        }
    }

    private boolean isPdf(byte[] header) {
        byte[] magic = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        int searchLimit = Math.min(1024, header.length - magic.length + 1);
        for (int offset = 0; offset < searchLimit; offset++) {
            boolean match = true;
            for (int index = 0; index < magic.length; index++) match &= header[offset + index] == magic[index];
            if (match) return true;
        }
        return false;
    }

    private boolean isMp4(byte[] header) {
        if (header.length < 16 || header[4] != 'f' || header[5] != 't' || header[6] != 'y' || header[7] != 'p') {
            return false;
        }
        long boxSize = ((long) Byte.toUnsignedInt(header[0]) << 24)
                | ((long) Byte.toUnsignedInt(header[1]) << 16)
                | ((long) Byte.toUnsignedInt(header[2]) << 8)
                | Byte.toUnsignedInt(header[3]);
        if (boxSize < 16 || boxSize % 4 != 0) return false;
        for (int index = 8; index < 12; index++) {
            int value = Byte.toUnsignedInt(header[index]);
            if (value < 0x20 || value > 0x7e) return false;
        }
        return true;
    }

    private MediaSafetyException unsupported(String message) {
        return new MediaSafetyException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "media_unreadable", message);
    }
}
