ALTER TABLE stores
MODIFY store_admin_id VARCHAR(36) NULL;
ALTER TABLE stores
ADD COLUMN last_store_admin_id VARCHAR(36) NULL;
UPDATE stores
SET last_store_admin_id = store_admin_id,
  store_admin_id = NULL
WHERE status = 'CLOSED'
  AND store_admin_id IS NOT NULL;