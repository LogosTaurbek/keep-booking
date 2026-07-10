-- V005: токены подтверждения email и восстановления пароля

CREATE TABLE user_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    purpose     VARCHAR(30)  NOT NULL
                    CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_tokens_user ON user_tokens(user_id, purpose);
