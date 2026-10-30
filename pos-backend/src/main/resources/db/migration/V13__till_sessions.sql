CREATE TABLE till_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(36) NOT NULL,
  store_id VARCHAR(36) NOT NULL,
  operator_public_id VARCHAR(36) NOT NULL,
  status VARCHAR(10) NOT NULL,
  opened_at DATETIME(6) NOT NULL,
  closed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  blocking_operator_id VARCHAR(36) GENERATED ALWAYS AS (
    IF(status <> 'CLOSED', operator_public_id, NULL)
  ) STORED,
  CONSTRAINT uk_till_sessions_public_id UNIQUE (public_id),
  CONSTRAINT uk_till_sessions_open_slot UNIQUE (blocking_operator_id),
  CONSTRAINT chk_till_sessions_status CHECK (
    status IN ('ACTIVE', 'LOCKED', 'CLOSED')
  ),
  KEY idx_till_sessions_store (store_id)
) ENGINE = InnoDB;