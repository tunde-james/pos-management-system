ALTER TABLE stores
ADD COLUMN open_time TIME NULL,
  ADD COLUMN close_time TIME NULL,
  ADD COLUMN working_days JSON NULL;