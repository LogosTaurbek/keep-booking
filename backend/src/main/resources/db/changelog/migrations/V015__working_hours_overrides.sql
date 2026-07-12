-- V015: per-date overrides for restaurant working hours (holidays, special days)
-- Takes precedence over the weekly schedule in restaurant_working_hours for the given date.

CREATE TABLE restaurant_working_hours_overrides (
    id            BIGSERIAL   PRIMARY KEY,
    restaurant_id BIGINT      NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    date          DATE        NOT NULL,
    open_time     TIME,
    close_time    TIME,
    is_closed     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, date)
);

CREATE INDEX idx_working_hours_overrides_restaurant_date ON restaurant_working_hours_overrides(restaurant_id, date);
