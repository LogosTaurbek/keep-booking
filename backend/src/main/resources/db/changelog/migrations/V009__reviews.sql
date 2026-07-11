-- V009: отзывы (только после COMPLETED-брони)

CREATE TABLE reviews (
    id            BIGSERIAL    PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    booking_id    BIGINT       NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
    rating        SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment       TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reviews_restaurant ON reviews(restaurant_id, created_at DESC);
CREATE INDEX idx_reviews_user       ON reviews(user_id);
