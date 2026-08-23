CREATE TABLE processor_definitions (
    id UUID PRIMARY KEY,
    slug VARCHAR(160) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    publisher VARCHAR(160) NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT processor_definitions_visibility_check
        CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);

CREATE TABLE processor_versions (
    id UUID PRIMARY KEY,
    processor_definition_id UUID NOT NULL
        REFERENCES processor_definitions(id) ON DELETE RESTRICT,
    semantic_version VARCHAR(64) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    implementation_key VARCHAR(240) NOT NULL UNIQUE,
    schema_version VARCHAR(32) NOT NULL,
    manifest JSONB NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    deprecated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT processor_versions_definition_version_key
        UNIQUE (processor_definition_id, semantic_version),
    CONSTRAINT processor_versions_lifecycle_check
        CHECK (lifecycle_status IN ('DRAFT', 'VALIDATING', 'PUBLISHED', 'DEPRECATED', 'DISABLED')),
    CONSTRAINT processor_versions_published_at_check
        CHECK (lifecycle_status NOT IN ('PUBLISHED', 'DEPRECATED', 'DISABLED') OR published_at IS NOT NULL)
);

CREATE INDEX idx_processor_versions_status
    ON processor_versions(lifecycle_status);
CREATE INDEX idx_processor_versions_definition
    ON processor_versions(processor_definition_id);
