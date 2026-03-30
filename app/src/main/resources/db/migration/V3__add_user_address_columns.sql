-- V3: Add embedded address columns (shipping + billing) to users table
ALTER TABLE users ADD COLUMN shipping_street   VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN shipping_city     VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN shipping_state    VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN shipping_zip_code VARCHAR(20)  NULL;
ALTER TABLE users ADD COLUMN shipping_country  VARCHAR(100) NULL;

ALTER TABLE users ADD COLUMN billing_street    VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN billing_city      VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN billing_state     VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN billing_zip_code  VARCHAR(20)  NULL;
ALTER TABLE users ADD COLUMN billing_country   VARCHAR(100) NULL;
