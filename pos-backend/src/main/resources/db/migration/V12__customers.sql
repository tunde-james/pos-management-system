CREATE TABLE customers (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(36) NOT NULL,
  loyalty_tag VARCHAR(8) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_customers_public_id UNIQUE (public_id),
  CONSTRAINT uk_customers_loyalty_tag UNIQUE (loyalty_tag),
  CONSTRAINT uk_customers_phone UNIQUE (phone)
) ENGINE = InnoDB;