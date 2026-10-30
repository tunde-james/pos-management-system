CREATE TABLE orders (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(36) NOT NULL,
  session_id BIGINT NOT NULL,
  customer_public_id VARCHAR(36) NULL,
  payment_method VARCHAR(10) NOT NULL,
  payment_reference VARCHAR(255) NULL,
  status VARCHAR(10) NOT NULL,
  total_kobo BIGINT NOT NULL,
  paid_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_orders_public_id UNIQUE (public_id),
  CONSTRAINT fk_orders_session FOREIGN KEY (session_id) REFERENCES till_sessions (id),
  CONSTRAINT chk_orders_payment_method CHECK (
    payment_method IN ('CASH', 'CARD', 'TRANSFER')
  ),
  CONSTRAINT chk_orders_status CHECK (status IN ('PAID')),
  CONSTRAINT chk_orders_total_kobo CHECK (total_kobo >= 0)
) ENGINE = InnoDB;
CREATE TABLE order_lines (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_public_id VARCHAR(36) NOT NULL,
  product_name VARCHAR(200) NOT NULL,
  unit_price_kobo BIGINT NOT NULL,
  quantity INT NOT NULL,
  line_total_kobo BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT chk_order_lines_quantity CHECK (
    quantity BETWEEN 1 AND 999
  ),
  CONSTRAINT chk_order_lines_money CHECK (
    unit_price_kobo >= 0
    AND line_total_kobo >= 0
  ),
  KEY idx_order_lines_order (order_id)
) ENGINE = InnoDB;