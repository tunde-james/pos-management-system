-- Widen status (PENDING_PAYMENT is 15 chars; the column was VARCHAR(10)) 
-- and open the CHECK to the four fates.
ALTER TABLE orders
MODIFY status VARCHAR(20) NOT NULL;
ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders
ADD CONSTRAINT chk_orders_status CHECK (
    status IN ('PENDING_PAYMENT', 'PAID', 'FAILED', 'EXPIRED')
  );
-- A pending order has not paid yet.
ALTER TABLE orders
MODIFY paid_at DATETIME(6) NULL;
-- The human-speakable order number (format: O- + 6 zero - padded digits).
-- Add nullable, backfill existing rows in insertion order, then lock.
ALTER TABLE orders
ADD order_number VARCHAR(12) NULL;
UPDATE orders o
  JOIN (
    SELECT id,
      ROW_NUMBER() OVER (
        ORDER BY id
      ) AS rn
    FROM orders
  ) t ON o.id = t.id
SET o.order_number = CONCAT('O-', LPAD(t.rn, 6, '0'));
ALTER TABLE orders
ADD CONSTRAINT uk_orders_number UNIQUE (order_number);
-- The chain-wide number counter: one row, id = 1, forever.
CREATE TABLE order_sequences (
  id INT NOT NULL PRIMARY KEY,
  current_value BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT chk_order_sequences_id CHECK (id = 1)
) ENGINE = InnoDB;
INSERT INTO order_sequences (id, current_value, created_at, updated_at)
SELECT 1,
  COALESCE(
    (
      SELECT MAX(CAST(SUBSTRING(order_number, 3) AS UNSIGNED))
      FROM orders
    ),
    0
  ),
  NOW(6),
  NOW(6);
-- Why a terminal order ended, and when a pending slip stops being valid.
ALTER TABLE orders
ADD status_reason VARCHAR(40) NULL,
  ADD expires_at DATETIME(6) NULL;