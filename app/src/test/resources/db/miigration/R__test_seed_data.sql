-- Repeatable migration: seeds test product (runs after all versioned migrations)
DELETE FROM products WHERE name = 'Integration Test Laptop';
INSERT INTO products (name, description, price, stock_quantity, image_url, created_at, updated_at)
VALUES ('Integration Test Laptop', 'A high-end laptop for testing', 1299.99, 50,
        'https://example.com/laptop.jpg', NOW(), NOW());