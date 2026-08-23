package com.sluice.api.pipeline.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineVersionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publishedDefinitionCannotBeUpdated() throws Exception {
        PipelineVersion version = new PipelineVersion(UUID.randomUUID(), null, 1, "DRAFT", "image/jpeg",
                mapper.readTree("{\"schemaVersion\":\"1\"}"));
        version.publish(mapper.createObjectNode(), null, null);

        assertThrows(IllegalStateException.class, () -> version.updateDraft(
                mapper.readTree("{\"schemaVersion\":\"2\"}"), "image/png", null, null, null));
        assertEquals("1", version.getDefinition().path("schemaVersion").asText());
    }
}
