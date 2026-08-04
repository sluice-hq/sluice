ALTER TABLE assets ADD COLUMN upload_status VARCHAR(50);
UPDATE assets SET upload_status = 'COMPLETED';
ALTER TABLE assets ALTER COLUMN upload_status SET NOT NULL;
