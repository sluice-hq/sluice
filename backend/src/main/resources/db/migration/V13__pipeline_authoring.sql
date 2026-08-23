ALTER TABLE pipelines ADD COLUMN slug VARCHAR(100);
ALTER TABLE pipelines ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

UPDATE pipelines
SET slug = 'pipeline-' || substring(replace(id::text, '-', '') from 1 for 12)
WHERE slug IS NULL;

ALTER TABLE pipelines ALTER COLUMN slug SET NOT NULL;
ALTER TABLE pipelines ADD CONSTRAINT pipelines_project_slug_unique UNIQUE (project_id, slug);
ALTER TABLE pipelines ADD CONSTRAINT pipelines_status_valid CHECK (status IN ('ACTIVE', 'DEPRECATED', 'ARCHIVED'));

ALTER TABLE pipeline_versions ADD COLUMN schema_version VARCHAR(16) NOT NULL DEFAULT '1';
ALTER TABLE pipeline_versions ADD COLUMN revision INT NOT NULL DEFAULT 1;
ALTER TABLE pipeline_versions ADD COLUMN validation_result JSONB;
ALTER TABLE pipeline_versions ADD COLUMN resolved_input_contract JSONB;
ALTER TABLE pipeline_versions ADD COLUMN resolved_output_contract JSONB;
ALTER TABLE pipeline_versions ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE pipeline_versions ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pipeline_versions ADD CONSTRAINT pipeline_versions_status_valid
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'));
ALTER TABLE pipeline_versions ADD CONSTRAINT pipeline_versions_revision_positive CHECK (revision > 0);

WITH ranked_drafts AS (
    SELECT id, row_number() OVER (PARTITION BY pipeline_id ORDER BY version_number DESC) AS draft_rank
    FROM pipeline_versions
    WHERE status = 'DRAFT'
)
UPDATE pipeline_versions
SET status = 'ARCHIVED'
WHERE id IN (SELECT id FROM ranked_drafts WHERE draft_rank > 1);

CREATE UNIQUE INDEX pipeline_versions_one_draft
    ON pipeline_versions(pipeline_id) WHERE status = 'DRAFT';

CREATE TABLE pipeline_aliases (
    pipeline_id UUID NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    alias VARCHAR(64) NOT NULL,
    pipeline_version_id UUID NOT NULL REFERENCES pipeline_versions(id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (pipeline_id, alias)
);

CREATE INDEX idx_pipeline_aliases_version ON pipeline_aliases(pipeline_version_id);
