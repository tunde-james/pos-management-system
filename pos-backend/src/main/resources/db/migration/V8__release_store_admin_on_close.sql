ALTER TABLE stores
MODIFY store_admin_id VARCHAR(36) NULL;
ALTER TABLE stores
ADD COLUMN last_store_admin_id VARCHAR(36) NULL;