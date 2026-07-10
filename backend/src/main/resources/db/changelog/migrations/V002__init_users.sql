-- V002: пользователи, роли, refresh-токены

CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    firstname      VARCHAR(100) NOT NULL,
    lastname       VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    country_id     BIGINT       REFERENCES countries(id),
    city_id        BIGINT       REFERENCES cities(id),
    avatar_url     VARCHAR(500),
    language       VARCHAR(10)  NOT NULL DEFAULT 'en',
    timezone       VARCHAR(50)  NOT NULL DEFAULT 'UTC',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED')),
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL CHECK (role IN (
                'ROLE_USER', 'ROLE_RESTAURANT_ADMIN',
                'ROLE_COMPANY_ADMIN', 'ROLE_SUPER_ADMIN')),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_status   ON users(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_refresh_tokens ON refresh_tokens(token_hash) WHERE revoked = FALSE;
