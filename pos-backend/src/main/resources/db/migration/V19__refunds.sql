CREATE TABLE refunds (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(36) NOT NULL,
  order_id BIGINT NOT NULL,
  total_kobo BIGINT NOT NULL,
  return_method VARCHAR(13) NULL,
  provider_refund_reference VARCHAR(255) NULL,
  actor_public_id VARCHAR(36) NOT NULL,
  refunded_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_refunds_public_id UNIQUE (public_id),
  CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT chk_refunds_return_method CHECK (
    return_method IN ('CASH', 'BANK_TRANSFER')
  ),
  CONSTRAINT chk_refunds_total_kobo CHECK (total_kobo > 0)
) ENGINE = InnoDB;
CREATE TABLE refund_lines (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  refund_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  product_public_id VARCHAR(36) NOT NULL,
  quantity INT NOT NULL,
  line_total_kobo BIGINT NOT NULL,
  disposition VARCHAR(10) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_refund_lines_refund FOREIGN KEY (refund_id) REFERENCES refunds (id),
  CONSTRAINT fk_refund_lines_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT uk_refund_lines_order_product UNIQUE (order_id, product_public_id),
  CONSTRAINT chk_refund_lines_disposition CHECK (
    disposition IN ('RESTOCKED', 'DAMAGED')
  ),
  CONSTRAINT chk_refund_lines_quantity CHECK (
    quantity BETWEEN 1 AND 999
  ),
  CONSTRAINT chk_refund_lines_money CHECK (line_total_kobo >= 0),
  KEY idx_refund_lines_refund (refund_id),
  KEY idx_refund_lines_order (order_id)
) ENGINE = InnoDB;