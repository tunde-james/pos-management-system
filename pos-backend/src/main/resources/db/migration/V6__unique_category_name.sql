ALTER TABLE categories
ADD CONSTRAINT uk_categories_name UNIQUE (name);