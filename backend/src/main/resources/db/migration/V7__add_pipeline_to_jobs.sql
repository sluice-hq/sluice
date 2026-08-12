ALTER TABLE jobs
ADD COLUMN pipeline_version_id UUID REFERENCES pipeline_versions(id);
