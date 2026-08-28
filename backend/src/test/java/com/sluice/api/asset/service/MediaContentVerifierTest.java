package com.sluice.api.asset.service;

import com.sluice.api.config.MediaSafetyException;
import com.sluice.api.pipeline.StreamMediaResource;
import com.sluice.api.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaContentVerifierTest {

    @Test
    void acceptsPdfMagicAndRejectsMislabeledPdfBytes() {
        assertDoesNotThrow(() -> verifier("%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII))
                .verify("blob", "application/pdf"));
        assertThrows(MediaSafetyException.class, () -> verifier("not a pdf".getBytes(StandardCharsets.US_ASCII))
                .verify("blob", "application/pdf"));
    }

    @Test
    void acceptsStructuredIsoBmffHeaderAndRejectsMislabeledMp4Bytes() {
        byte[] mp4 = new byte[] {
                0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0,
                'i', 's', 'o', 'm', 'm', 'p', '4', '2'
        };
        assertDoesNotThrow(() -> verifier(mp4).verify("blob", "video/mp4"));
        assertThrows(MediaSafetyException.class, () -> verifier("arbitrary bytes".getBytes(StandardCharsets.US_ASCII))
                .verify("blob", "video/mp4"));
    }

    @Test
    void rejectsTypesWithoutAnImplementedVerifier() {
        assertThrows(MediaSafetyException.class, () -> verifier(new byte[] { 1, 2, 3 })
                .verify("blob", "application/zip"));
    }

    private MediaContentVerifier verifier(byte[] bytes) {
        StorageService storage = mock(StorageService.class);
        when(storage.downloadFile("blob")).thenReturn(new StreamMediaResource(new ByteArrayInputStream(bytes), bytes.length));
        return new MediaContentVerifier(storage);
    }
}
