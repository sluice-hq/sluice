ALTER TABLE pipelines ADD COLUMN project_id UUID;
ALTER TABLE pipelines ADD CONSTRAINT fk_pipelines_project_id FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;
CREATE INDEX idx_pipelines_project_id ON pipelines(project_id);

ALTER TABLE assets ADD COLUMN project_id UUID;
ALTER TABLE assets ADD CONSTRAINT fk_assets_project_id FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;
CREATE INDEX idx_assets_project_id ON assets(project_id);

ALTER TABLE jobs ADD COLUMN project_id UUID;
ALTER TABLE jobs ADD CONSTRAINT fk_jobs_project_id FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;
CREATE INDEX idx_jobs_project_id ON jobs(project_id);
