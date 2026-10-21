CREATE TABLE store_employees (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(36) NOT NULL,
  store_id VARCHAR(36) NOT NULL,
  user_public_id VARCHAR(36) NOT NULL,
  job_title VARCHAR(20) NOT NULL,
  hired_at DATETIME(6) NOT NULL,
  terminated_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  active_user_id VARCHAR(36) GENERATED ALWAYS AS (
    IF(terminated_at IS NULL, user_public_id, NULL)
  ) STORED,
  CONSTRAINT uk_store_employees_public_id UNIQUE (public_id),
  CONSTRAINT uk_store_employees_active_user UNIQUE (active_user_id),
  CONSTRAINT chk_store_employees_title CHECK (
    job_title IN ('CASHIER', 'SALES_ATTENDANT', 'MANAGER')
  ),
  KEY idx_store_employees_store (store_id)
) ENGINE = InnoDB;