package com.sluice.api.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureBlobStorageServiceTest {
    @Test
    void replacesOnlyTheInternalStorageAccountBase() {
        String publicUrl = AzureBlobStorageService.replaceAccountBase(
                "http://azurite:10000/devstoreaccount1/",
                "http://localhost:10000/devstoreaccount1/",
                "http://azurite:10000/devstoreaccount1/assets/folder%2Fimage.png");

        assertThat(publicUrl)
                .isEqualTo("http://localhost:10000/devstoreaccount1/assets/folder%2Fimage.png");
    }

    @Test
    void rejectsUrlsFromAnotherStorageEndpoint() {
        assertThatThrownBy(() -> AzureBlobStorageService.replaceAccountBase(
                "http://azurite:10000/devstoreaccount1",
                "http://localhost:10000/devstoreaccount1",
                "http://untrusted.example/assets/image.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage account endpoint");
    }
}
