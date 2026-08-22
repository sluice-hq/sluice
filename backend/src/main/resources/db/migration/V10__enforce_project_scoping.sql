ALTER TABLE pipelines
    ADD CONSTRAINT pipelines_project_id_required CHECK (project_id IS NOT NULL) NOT VALID;

ALTER TABLE assets
    ADD CONSTRAINT assets_project_id_required CHECK (project_id IS NOT NULL) NOT VALID;

ALTER TABLE jobs
    ADD CONSTRAINT jobs_project_id_required CHECK (project_id IS NOT NULL) NOT VALID;
