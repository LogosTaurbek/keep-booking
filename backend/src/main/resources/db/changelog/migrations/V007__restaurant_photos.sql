-- V007: фотографии ресторана

CREATE TABLE restaurant_photos (
    id            BIGSERIAL    PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    url           VARCHAR(500) NOT NULL,
    position      INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_restaurant_photos_restaurant ON restaurant_photos(restaurant_id);
