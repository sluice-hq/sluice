package com.sluice.api.pipeline.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.sluice.api.pipeline.ProcessorManifest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ProcessorConfigurationValidator {
    private final JsonSchemaFactory schemaFactory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public void validate(ProcessorManifest manifest, JsonNode configuration) {
        JsonNode instance = configuration == null
                ? JsonNodeFactory.instance.objectNode()
                : configuration;
        JsonSchema schema = schemaFactory.getSchema(manifest.configSchema());
        List<ConfigurationValidationError> errors = schema.validate(instance).stream()
                .map(this::toError)
                .sorted(Comparator.comparing(ConfigurationValidationError::path)
                        .thenComparing(ConfigurationValidationError::code))
                .toList();
        if (!errors.isEmpty()) {
            throw new ProcessorConfigurationException(manifest.key(), errors);
        }
    }

    private ConfigurationValidationError toError(ValidationMessage validationMessage) {
        String code = validationMessage.getType();
        return new ConfigurationValidationError(
                toJsonPointer(validationMessage.getInstanceLocation().toString()),
                code == null ? "schema" : code,
                safeMessage(code));
    }

    private String safeMessage(String code) {
        if ("additionalProperties".equals(code)) return "Unknown configuration field.";
        if ("type".equals(code)) return "Configuration value has the wrong type.";
        if ("required".equals(code)) return "Required configuration field is missing.";
        if ("minimum".equals(code) || "exclusiveMinimum".equals(code)) return "Configuration value is below the allowed minimum.";
        if ("maximum".equals(code) || "exclusiveMaximum".equals(code)) return "Configuration value exceeds the allowed maximum.";
        return "Configuration does not satisfy the processor schema.";
    }

    private String toJsonPointer(String schemaPath) {
        if (schemaPath == null || schemaPath.isBlank() || "$".equals(schemaPath)) return "";
        String normalized = schemaPath.startsWith("$.") ? schemaPath.substring(2) : schemaPath;
        if (normalized.startsWith("$[")) normalized = normalized.substring(1);
        return "/" + normalized.replace("[", "/").replace("]", "").replace(".", "/")
                .replace("~", "~0");
    }
}
