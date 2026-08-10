ALTER TABLE assets ADD COLUMN parent_asset_id UUID;
ALTER TABLE assets ADD CONSTRAINT fk_parent_asset FOREIGN KEY (parent_asset_id) REFERENCES assets(id);
