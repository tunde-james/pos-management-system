ALTER TABLE orders
ADD provider VARCHAR(10) NULL,
  ADD provider_url VARCHAR(500) NULL,
  ADD provider_account_number VARCHAR(30) NULL,
  ADD provider_bank_name VARCHAR(100) NULL,
  ADD provider_bank_code VARCHAR(10) NULL,
  ADD CONSTRAINT chk_orders_provider CHECK (provider IN ('PAYSTACK', 'MONNIFY'));