DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pipelines WHERE project_id IS NULL)
        OR EXISTS (SELECT 1 FROM assets WHERE project_id IS NULL)
        OR EXISTS (SELECT 1 FROM jobs WHERE project_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot enforce project scoping while pipelines, assets, or jobs have a null project_id';
    END IF;
END $$;

ALTER TABLE pipelines VALIDATE CONSTRAINT pipelines_project_id_required;
ALTER TABLE assets VALIDATE CONSTRAINT assets_project_id_required;
ALTER TABLE jobs VALIDATE CONSTRAINT jobs_project_id_required;

ALTER TABLE pipelines ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE assets ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN project_id SET NOT NULL;

ALTER TABLE assets ADD COLUMN producing_job_id UUID REFERENCES jobs(id) ON DELETE SET NULL;
CREATE INDEX idx_assets_producing_job_id ON assets(producing_job_id);
