package com.sluice.api.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.pipeline.domain.Pipeline;
import com.sluice.api.pipeline.domain.PipelineAlias;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.repository.PipelineAliasRepository;
import com.sluice.api.pipeline.repository.PipelineRepository;
import com.sluice.api.pipeline.repository.PipelineVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PipelineService {
    private static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9-]{1,99}$");
    private static final Pattern ALIAS = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");

    private final PipelineRepository pipelines;
    private final PipelineVersionRepository versions;
    private final PipelineAliasRepository aliases;
    private final PipelineValidator validator;
    private final ObjectMapper objectMapper;

    public PipelineService(PipelineRepository pipelines, PipelineVersionRepository versions,
                           PipelineAliasRepository aliases, PipelineValidator validator, ObjectMapper objectMapper) {
        this.pipelines = pipelines;
        this.versions = versions;
        this.aliases = aliases;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PipelineDetail createPipeline(String name, String description, JsonNode definition, ProjectContext context) {
        requireName(name);
        String slug = definition == null ? "" : definition.path("slug").asText();
        requireSlug(slug);
        Pipeline pipeline = pipelines.save(new Pipeline(UUID.randomUUID(), slug, name.trim(), description, context.getProjectId()));
        PipelineVersion draft = new PipelineVersion(UUID.randomUUID(), pipeline, 1, "DRAFT",
                expectedMime(definition), definition.deepCopy());
        PipelineValidationReport report = validator.validateDefinition(slug, definition);
        draft.recordValidation(tree(report), tree(report.inputContract()), tree(report.outputContract()));
        versions.save(draft);
        return detail(pipeline);
    }

    @Transactional(readOnly = true)
    public List<PipelineSummary> list(ProjectContext context) {
        return pipelines.findByProjectId(context.getProjectId()).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public PipelineDetail get(String slug, ProjectContext context) {
        return detail(findPipeline(slug, context));
    }

    @Transactional(readOnly = true)
    public List<PipelineVersionView> history(String slug, ProjectContext context) {
        Pipeline pipeline = findPipeline(slug, context);
        return versions.findByPipelineIdOrderByVersionNumberDesc(pipeline.getId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public PipelineVersionView version(String slug, int number, ProjectContext context) {
        Pipeline pipeline = findPipeline(slug, context);
        return view(versions.findByPipelineIdAndVersionNumber(pipeline.getId(), number)
                .orElseThrow(() -> new IllegalArgumentException("Pipeline version not found")));
    }

    @Transactional
    public PipelineVersionView updateDraft(String slug, int expectedRevision, JsonNode definition, ProjectContext context) {
        Pipeline pipeline = findPipelineForUpdate(slug, context);
        if (!slug.equals(definition.path("slug").asText())) throw new IllegalArgumentException("Definition slug does not match pipeline");
        PipelineValidationReport report = validator.validateDefinition(slug, definition);
        Optional<PipelineVersion> existing = versions.findFirstByPipelineIdAndStatus(pipeline.getId(), "DRAFT");
        PipelineVersion draft;
        if (existing.isEmpty()) {
            if (expectedRevision != 0) throw new IllegalStateException("Draft revision is stale");
            draft = new PipelineVersion(UUID.randomUUID(), pipeline, versions.getMaxVersionNumber(pipeline.getId()) + 1,
                    "DRAFT", expectedMime(definition), definition.deepCopy());
            draft.recordValidation(tree(report), tree(report.inputContract()), tree(report.outputContract()));
        } else {
            draft = existing.get();
            if (draft.getRevision() != expectedRevision) throw new IllegalStateException("Draft revision is stale");
            draft.updateDraft(definition.deepCopy(), expectedMime(definition), tree(report),
                    tree(report.inputContract()), tree(report.outputContract()));
        }
        return view(versions.save(draft));
    }

    @Transactional
    public PipelineValidationReport validateDraft(String slug, JsonNode candidate, ProjectContext context) {
        Pipeline pipeline = findPipeline(slug, context);
        PipelineVersion draft = versions.findFirstByPipelineIdAndStatus(pipeline.getId(), "DRAFT")
                .orElseThrow(() -> new IllegalStateException("Pipeline has no draft"));
        JsonNode definition = candidate == null ? draft.getDefinition() : candidate;
        PipelineValidationReport report = validator.validateDefinition(slug, definition);
        if (candidate == null) {
            draft.recordValidation(tree(report), tree(report.inputContract()), tree(report.outputContract()));
            versions.save(draft);
        }
        return report;
    }

    @Transactional
    public PipelineVersionView publish(String slug, int expectedRevision, ProjectContext context) {
        Pipeline pipeline = findPipelineForUpdate(slug, context);
        PipelineVersion draft = versions.findFirstByPipelineIdAndStatus(pipeline.getId(), "DRAFT")
                .orElseThrow(() -> new IllegalStateException("Pipeline has no draft"));
        if (draft.getRevision() != expectedRevision) throw new IllegalStateException("Draft revision is stale");
        PipelineValidationReport report = validator.validateDefinition(slug, draft.getDefinition());
        if (!report.valid()) throw new PipelineValidationException(report);
        draft.publish(tree(report), tree(report.inputContract()), tree(report.outputContract()));
        PipelineVersion published = versions.save(draft);
        aliases.findByPipelineIdAndAlias(pipeline.getId(), "stable")
                .orElseGet(() -> aliases.save(new PipelineAlias(pipeline.getId(), "stable", published)));
        return view(published);
    }

    @Transactional
    public PipelineAliasView moveAlias(String slug, String alias, int versionNumber, ProjectContext context) {
        if (!ALIAS.matcher(alias).matches()) throw new IllegalArgumentException("Alias must be a lowercase slug");
        Pipeline pipeline = findPipelineForUpdate(slug, context);
        PipelineVersion target = versions.findByPipelineIdAndVersionNumber(pipeline.getId(), versionNumber)
                .orElseThrow(() -> new IllegalArgumentException("Pipeline version not found"));
        if (!"PUBLISHED".equals(target.getStatus()) && !"DEPRECATED".equals(target.getStatus())) {
            throw new IllegalStateException("Aliases may target only immutable published versions");
        }
        PipelineAlias pipelineAlias = aliases.findByPipelineIdAndAlias(pipeline.getId(), alias)
                .orElseGet(() -> new PipelineAlias(pipeline.getId(), alias, target));
        pipelineAlias.moveTo(target);
        aliases.save(pipelineAlias);
        return new PipelineAliasView(alias, versionNumber);
    }

    @Transactional(readOnly = true)
    public Optional<PipelineVersion> getLatestPublishedVersion(UUID pipelineId, ProjectContext context) {
        Pipeline pipeline = pipelines.findByIdAndProjectId(pipelineId, context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Pipeline not found"));
        return aliases.findByPipelineIdAndAlias(pipeline.getId(), "stable")
                .map(PipelineAlias::getPipelineVersion)
                .or(() -> versions.findFirstByPipelineIdAndStatusOrderByVersionNumberDesc(pipeline.getId(), "PUBLISHED"));
    }

    /**
     * Resolves the public pipeline name to an immutable published version.
     * A run may pin a version explicitly or use a project-scoped alias (stable by default).
     */
    @Transactional(readOnly = true)
    public PipelineVersion resolvePublishedVersion(String slug, String alias, Integer versionNumber,
                                                   ProjectContext context) {
        Pipeline pipeline = findPipeline(slug, context);
        if (versionNumber != null && alias != null && !alias.isBlank()) {
            throw new IllegalArgumentException("Specify either pipeline version or alias, not both");
        }

        PipelineVersion version;
        if (versionNumber != null) {
            version = versions.findByPipelineIdAndVersionNumber(pipeline.getId(), versionNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Pipeline version not found"));
        } else {
            String selectedAlias = alias == null || alias.isBlank() ? "stable" : alias;
            version = aliases.findByPipelineIdAndAlias(pipeline.getId(), selectedAlias)
                    .map(PipelineAlias::getPipelineVersion)
                    .orElseThrow(() -> new IllegalArgumentException("Pipeline alias not found"));
        }

        if (!"PUBLISHED".equals(version.getStatus()) && !"DEPRECATED".equals(version.getStatus())) {
            throw new IllegalArgumentException("Runs may target only published pipeline versions");
        }
        return version;
    }

    @Transactional(readOnly = true)
    public List<PublishedPipeline> getPublishedPipelines(ProjectContext context) {
        return pipelines.findByProjectId(context.getProjectId()).stream()
                .map(pipeline -> aliases.findByPipelineIdAndAlias(pipeline.getId(), "stable")
                        .map(PipelineAlias::getPipelineVersion)
                        .map(version -> new PublishedPipeline(pipeline.getId(), pipeline.getSlug(), pipeline.getName(), pipeline.getDescription(),
                                version.getId(), version.getVersionNumber(), version.getExpectedInputMimeType(),
                                publishedInputContract(version)))
                        .orElse(null))
                .filter(Objects::nonNull).toList();
    }

    private JsonNode publishedInputContract(PipelineVersion version) {
        JsonNode resolved = normalizeInputContract(version.getResolvedInputContract());
        if (resolved != null) return resolved;

        JsonNode definition = version.getDefinition();
        JsonNode legacy = normalizeInputContract(definition != null && definition.isObject()
                ? definition.get("input") : null);
        if (legacy != null) return legacy;

        var disabled = objectMapper.createObjectNode();
        disabled.put("kind", "unknown");
        var mimeTypes = disabled.putArray("mimeTypes");
        String expected = version.getExpectedInputMimeType();
        if (expected != null && !expected.isBlank() && !"*/*".equals(expected)) mimeTypes.add(expected);
        disabled.put("maxBytes", 0);
        disabled.put("maxPixels", 0);
        disabled.put("alphaSupported", false);
        disabled.put("animationSupported", false);
        return disabled;
    }

    private JsonNode normalizeInputContract(JsonNode candidate) {
        if (candidate == null || !candidate.isObject()) return null;
        JsonNode mimeTypes = candidate.get("mimeTypes");
        JsonNode maxBytes = candidate.get("maxBytes");
        if (mimeTypes == null || !mimeTypes.isArray() || mimeTypes.isEmpty()
                || maxBytes == null || !maxBytes.isIntegralNumber() || maxBytes.asLong() <= 0) return null;
        for (JsonNode mimeType : mimeTypes) {
            if (!mimeType.isTextual() || mimeType.asText().isBlank()) return null;
        }

        var normalized = objectMapper.createObjectNode();
        String kind = candidate.path("kind").asText("");
        normalized.put("kind", kind.isBlank() ? "unknown" : kind);
        normalized.set("mimeTypes", mimeTypes.deepCopy());
        normalized.put("maxBytes", maxBytes.asLong());
        normalized.put("maxPixels", Math.max(0, candidate.path("maxPixels").asLong(0)));
        normalized.put("alphaSupported", candidate.path("alphaSupported").asBoolean(false));
        normalized.put("animationSupported", candidate.path("animationSupported").asBoolean(false));
        return normalized;
    }

    private Pipeline findPipeline(String slug, ProjectContext context) {
        return pipelines.findBySlugAndProjectId(slug, context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Pipeline not found"));
    }

    private Pipeline findPipelineForUpdate(String slug, ProjectContext context) {
        Pipeline pipeline = findPipeline(slug, context);
        return pipelines.findByIdAndProjectIdForUpdate(pipeline.getId(), context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Pipeline not found"));
    }

    private PipelineDetail detail(Pipeline pipeline) {
        PipelineVersionView draft = versions.findFirstByPipelineIdAndStatus(pipeline.getId(), "DRAFT").map(this::view).orElse(null);
        List<PipelineAliasView> aliasViews = aliases.findByPipelineId(pipeline.getId()).stream()
                .map(value -> new PipelineAliasView(value.getAlias(), value.getPipelineVersion().getVersionNumber())).toList();
        return new PipelineDetail(summary(pipeline), draft, aliasViews);
    }

    private PipelineSummary summary(Pipeline pipeline) {
        PipelineVersion draft = versions.findFirstByPipelineIdAndStatus(pipeline.getId(), "DRAFT").orElse(null);
        Integer stable = aliases.findByPipelineIdAndAlias(pipeline.getId(), "stable")
                .map(value -> value.getPipelineVersion().getVersionNumber()).orElse(null);
        return new PipelineSummary(pipeline.getId(), pipeline.getSlug(), pipeline.getName(), pipeline.getDescription(),
                pipeline.getStatus(), draft == null ? null : draft.getVersionNumber(),
                draft == null ? null : draft.getRevision(), stable, pipeline.getCreatedAt(), pipeline.getUpdatedAt());
    }

    private PipelineVersionView view(PipelineVersion version) {
        return new PipelineVersionView(version.getId(), version.getVersionNumber(), version.getStatus(),
                version.getRevision(), version.getSchemaVersion(), version.getDefinition(), version.getValidationResult(),
                version.getResolvedInputContract(), version.getResolvedOutputContract(), version.getCreatedAt(), version.getPublishedAt());
    }

    private String expectedMime(JsonNode definition) {
        JsonNode mimeTypes = definition == null ? null : definition.path("input").path("mimeTypes");
        return mimeTypes != null && mimeTypes.isArray() && !mimeTypes.isEmpty() ? mimeTypes.get(0).asText("*/*") : "*/*";
    }

    private JsonNode tree(Object value) { return value == null ? null : objectMapper.valueToTree(value); }
    private void requireName(String name) { if (name == null || name.isBlank()) throw new IllegalArgumentException("Pipeline name is required"); }
    private void requireSlug(String slug) { if (!SLUG.matcher(slug).matches()) throw new IllegalArgumentException("Pipeline slug must be a lowercase slug"); }

    public record PipelineSummary(UUID id, String slug, String name, String description, String status,
                                  Integer draftVersion, Integer draftRevision, Integer stableVersion,
                                  Instant createdAt, Instant updatedAt) {}
    public record PipelineDetail(PipelineSummary pipeline, PipelineVersionView draft, List<PipelineAliasView> aliases) {}
    public record PipelineVersionView(UUID id, int versionNumber, String status, int revision, String schemaVersion,
                                      JsonNode definition, JsonNode validation, JsonNode resolvedInputContract,
                                      JsonNode resolvedOutputContract, Instant createdAt, Instant publishedAt) {}
    public record PipelineAliasView(String alias, int versionNumber) {}
    public record PublishedPipeline(UUID id, String slug, String name, String description, UUID versionId,
                                    int versionNumber, String expectedInputMimeType, JsonNode inputContract) {}
}
