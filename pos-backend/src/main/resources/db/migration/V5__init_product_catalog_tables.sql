CREATE TABLE categories (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(36) NOT NULL,
  name VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_categories_public_id UNIQUE (public_id)
) ENGINE = InnoDB;
CREATE TABLE products (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(36) NOT NULL,
  name VARCHAR(200) NOT NULL,
  sku VARCHAR(64) NOT NULL,
  description VARCHAR(2000) NULL,
  brand VARCHAR(100) NULL,
  image VARCHAR(500) NULL,
  category_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_products_public_id UNIQUE (public_id),
  CONSTRAINT uk_products_sku UNIQUE (sku),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB;
CREATE TABLE store_products (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  store_id VARCHAR(36) NOT NULL,
  product_id BIGINT NOT NULL,
  maximum_retail_price DECIMAL(12, 2) NOT NULL,
  selling_price DECIMAL(12, 2) NOT NULL,
  discount_percentage DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_store_products_store_product UNIQUE (store_id, product_id),
  CONSTRAINT fk_store_products_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB;