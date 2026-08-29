ALTER TABLE assets
    ADD COLUMN external_subject_id VARCHAR(128),
    ADD COLUMN external_reference VARCHAR(255);

CREATE INDEX idx_assets_project_external_subject
    ON assets(project_id, external_subject_id)
    WHERE external_subject_id IS NOT NULL;

CREATE INDEX idx_assets_project_external_reference
    ON assets(project_id, external_reference)
    WHERE external_reference IS NOT NULL;
