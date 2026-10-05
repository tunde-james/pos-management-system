CREATE TABLE stores (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(36) NOT NULL,
  brand VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  store_type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  store_admin_id VARCHAR(36) NOT NULL,
  address VARCHAR(255) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  email VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_stores_public_id UNIQUE (public_id),
  CONSTRAINT uk_stores_brand UNIQUE (brand),
  CONSTRAINT uk_stores_email UNIQUE (email),
  CONSTRAINT uk_stores_store_admin_id UNIQUE (store_admin_id)
) ENGINE = InnoDB;