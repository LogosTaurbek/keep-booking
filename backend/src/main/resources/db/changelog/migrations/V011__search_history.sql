-- V011: история поисков ресторанов

CREATE TABLE search_history (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255),
    cuisine_slug VARCHAR(60),
    city_id     BIGINT       REFERENCES cities(id),
    min_rating  DECIMAL(3,2),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_search_history_user ON search_history(user_id, created_at DESC);
