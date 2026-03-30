CREATE TABLE refresh_tokens
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    token      VARCHAR(512) NOT NULL,
    user_id    BIGINT       NOT NULL,
    expires_at datetime     NOT NULL,
    revoked    BIT(1)       NOT NULL,
    created_at datetime     NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id)
);

ALTER TABLE refresh_tokens
    ADD CONSTRAINT uc_refresh_tokens_token UNIQUE (token);

ALTER TABLE refresh_tokens
    ADD CONSTRAINT FK_REFRESH_TOKENS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);