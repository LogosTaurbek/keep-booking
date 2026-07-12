-- V018: analytics read model (tz2.txt §15/§25) — per-restaurant-per-day rollups, refreshed by a
-- scheduled worker instead of AnalyticsService aggregating over the live `bookings` table on
-- every read. unique-guest counts stay a live COUNT(DISTINCT) query (see AnalyticsService) - an
-- accurate distinct count isn't reconstructable from daily rollups without a sketch structure,
-- and that single query was never the expensive part of the original implementation.

CREATE TABLE restaurant_daily_stats (
    id              BIGSERIAL    PRIMARY KEY,
    restaurant_id   BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    stat_date       DATE         NOT NULL,
    pending_count   INT          NOT NULL DEFAULT 0,
    confirmed_count INT          NOT NULL DEFAULT 0,
    rejected_count  INT          NOT NULL DEFAULT 0,
    cancelled_count INT          NOT NULL DEFAULT 0,
    completed_count INT          NOT NULL DEFAULT 0,
    no_show_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, stat_date)
);
CREATE INDEX idx_restaurant_daily_stats_range ON restaurant_daily_stats(restaurant_id, stat_date);

CREATE TABLE restaurant_daily_hour_stats (
    id              BIGSERIAL    PRIMARY KEY,
    restaurant_id   BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    stat_date       DATE         NOT NULL,
    hour_of_day     INT          NOT NULL,
    booking_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, stat_date, hour_of_day)
);
CREATE INDEX idx_restaurant_daily_hour_stats_range ON restaurant_daily_hour_stats(restaurant_id, stat_date);

CREATE TABLE restaurant_daily_table_stats (
    id              BIGSERIAL    PRIMARY KEY,
    restaurant_id   BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    stat_date       DATE         NOT NULL,
    table_id        BIGINT       NOT NULL REFERENCES restaurant_tables(id) ON DELETE CASCADE,
    booking_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, stat_date, table_id)
);
CREATE INDEX idx_restaurant_daily_table_stats_range ON restaurant_daily_table_stats(restaurant_id, stat_date);

-- Single-row watermark: the refresh worker recomputes only (restaurant, date) pairs touched by a
-- booking write since this timestamp, instead of rescanning the whole bookings table every cycle.
CREATE TABLE analytics_refresh_state (
    id                BIGSERIAL   PRIMARY KEY,
    last_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT '1970-01-01T00:00:00Z'
);
INSERT INTO analytics_refresh_state (last_refreshed_at) VALUES ('1970-01-01T00:00:00Z');
