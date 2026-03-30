CREATE TABLE cart_items
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    cart_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    CONSTRAINT pk_cart_items PRIMARY KEY (id)
);

CREATE TABLE carts
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at datetime NOT NULL,
    CONSTRAINT pk_carts PRIMARY KEY (id)
);

CREATE TABLE order_items
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id)
);

CREATE TABLE orders
(
    id               BIGINT AUTO_INCREMENT NOT NULL,
    user_id          BIGINT         NOT NULL,
    total_price      DECIMAL(10, 2) NOT NULL,
    shipping_address VARCHAR(500)   NOT NULL,
    order_status     VARCHAR(20)    NOT NULL,
    payment_status   VARCHAR(20)    NOT NULL,
    created_at       datetime       NOT NULL,
    updated_at       datetime NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE TABLE payments
(
    id              BIGINT AUTO_INCREMENT NOT NULL,
    order_id        BIGINT         NOT NULL,
    payment_gateway VARCHAR(20)    NOT NULL,
    payment_id      VARCHAR(200) NULL,
    amount          DECIMAL(10, 2) NOT NULL,
    currency        VARCHAR(10) NULL,
    status          VARCHAR(20)    NOT NULL,
    failure_reason  VARCHAR(500) NULL,
    created_at      datetime       NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id)
);

CREATE TABLE products
(
    id             BIGINT AUTO_INCREMENT NOT NULL,
    name           VARCHAR(200)   NOT NULL,
    `description`  TEXT NULL,
    price          DECIMAL(10, 2) NOT NULL,
    stock_quantity INT            NOT NULL,
    image_url      VARCHAR(500) NULL,
    created_at     datetime       NOT NULL,
    updated_at     datetime NULL,
    CONSTRAINT pk_products PRIMARY KEY (id)
);

CREATE TABLE roles
(
    id   BIGINT AUTO_INCREMENT NOT NULL,
    name VARCHAR(20) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id)
);

CREATE TABLE user_roles
(
    role_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (role_id, user_id)
);

CREATE TABLE users
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    phone      VARCHAR(20) NULL,
    created_at datetime     NOT NULL,
    updated_at datetime NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE carts
    ADD CONSTRAINT uc_carts_user UNIQUE (user_id);

ALTER TABLE payments
    ADD CONSTRAINT uc_payments_order UNIQUE (order_id);

ALTER TABLE roles
    ADD CONSTRAINT uc_roles_name UNIQUE (name);

ALTER TABLE users
    ADD CONSTRAINT uc_users_email UNIQUE (email);

ALTER TABLE cart_items
    ADD CONSTRAINT uk_cart_product UNIQUE (cart_id, product_id);

ALTER TABLE carts
    ADD CONSTRAINT FK_CARTS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE cart_items
    ADD CONSTRAINT FK_CART_ITEMS_ON_CART FOREIGN KEY (cart_id) REFERENCES carts (id);

ALTER TABLE cart_items
    ADD CONSTRAINT FK_CART_ITEMS_ON_PRODUCT FOREIGN KEY (product_id) REFERENCES products (id);

ALTER TABLE orders
    ADD CONSTRAINT FK_ORDERS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE order_items
    ADD CONSTRAINT FK_ORDER_ITEMS_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);

ALTER TABLE order_items
    ADD CONSTRAINT FK_ORDER_ITEMS_ON_PRODUCT FOREIGN KEY (product_id) REFERENCES products (id);

ALTER TABLE payments
    ADD CONSTRAINT FK_PAYMENTS_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);

ALTER TABLE user_roles
    ADD CONSTRAINT fk_userol_on_role FOREIGN KEY (role_id) REFERENCES roles (id);

ALTER TABLE user_roles
    ADD CONSTRAINT fk_userol_on_user FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_cart_user ON carts(user_id);
CREATE INDEX idx_cartitem_cart ON cart_items(cart_id);
CREATE INDEX idx_order_user ON orders(user_id);
CREATE INDEX idx_orderitem_order ON order_items(order_id);
CREATE INDEX idx_payment_order ON payments(order_id);

INSERT INTO roles (name) VALUES ('ROLE_USER');
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');