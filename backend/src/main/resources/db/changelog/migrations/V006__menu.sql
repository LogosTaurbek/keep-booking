-- V006: меню ресторана

CREATE TABLE menu_items (
    id            BIGSERIAL     PRIMARY KEY,
    restaurant_id BIGINT        NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name          VARCHAR(255)  NOT NULL,
    description   TEXT,
    price         DECIMAL(10,2) NOT NULL,
    category      VARCHAR(100),
    photo_url     VARCHAR(500),
    is_available  BOOLEAN       NOT NULL DEFAULT TRUE,
    position      INTEGER       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_menu_items_restaurant ON menu_items(restaurant_id, category);
