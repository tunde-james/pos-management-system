CREATE TABLE inventories (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  store_id VARCHAR(36) NOT NULL,
  product_public_id VARCHAR(36) NOT NULL,
  quantity INT NOT NULL,
  low_stock_threshold INT NOT NULL,
  archived_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_inventories_store_product UNIQUE (store_id, product_public_id),
  CONSTRAINT chk_inventories_quantity CHECK (quantity >= 0),
  CONSTRAINT chk_inventories_threshold CHECK (low_stock_threshold >= 0)
) ENGINE = InnoDB;
CREATE TABLE stock_movements (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  store_id VARCHAR(36) NOT NULL,
  product_public_id VARCHAR(36) NOT NULL,
  movement_type VARCHAR(20) NOT NULL,
  quantity_delta INT NOT NULL,
  before_quantity INT NOT NULL,
  after_quantity INT NOT NULL,
  reason VARCHAR(500) NULL,
  performed_by VARCHAR(36) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT chk_movements_ledger CHECK (
    after_quantity = before_quantity + quantity_delta
  )
) ENGINE = InnoDB;