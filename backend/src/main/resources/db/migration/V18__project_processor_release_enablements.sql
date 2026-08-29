CREATE TABLE project_processor_release_enablements (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL
        REFERENCES projects(id) ON DELETE CASCADE,
    processor_version_id UUID NOT NULL
        REFERENCES processor_versions(id) ON DELETE RESTRICT,
    enabled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT project_processor_release_enablements_project_version_key
        UNIQUE (project_id, processor_version_id)
);

CREATE INDEX idx_project_processor_release_enablements_version
    ON project_processor_release_enablements(processor_version_id);
