-- The application check alone cannot serialize two concurrent assignments of the same asset.
-- Closed inspections release the unique key and remain in the asset's history.
ALTER TABLE inspection ADD COLUMN open_asset_id VARCHAR(200)
    GENERATED ALWAYS AS (CASE WHEN status <> 'CLOSED' THEN asset_id ELSE NULL END);
CREATE UNIQUE INDEX ux_inspection_open_asset ON inspection (open_asset_id);
