-- V014: device tokens для push-уведомлений (Firebase Cloud Messaging)

CREATE TABLE device_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    platform    VARCHAR(10)  NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);
