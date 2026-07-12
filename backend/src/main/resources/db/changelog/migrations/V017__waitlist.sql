-- V017: waitlist (tz2.txt §11.4 / §25 этап 3) — join when no table is free for a slot,
-- get notified when a matching booking at that restaurant/date/time is cancelled or rejected.

CREATE TABLE waitlist_entries (
    id            BIGSERIAL    PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    booking_date  DATE         NOT NULL,
    time_from     TIME         NOT NULL,
    time_to       TIME         NOT NULL,
    guest_count   INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'NOTIFIED', 'CANCELLED')),
    notified_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- One active wait per user per restaurant/date/time slot - re-joining the same slot is a no-op.
CREATE UNIQUE INDEX idx_waitlist_unique_active
    ON waitlist_entries(user_id, restaurant_id, booking_date, time_from, time_to)
    WHERE status = 'ACTIVE';

-- The lookup a freed booking triggers: active entries for this restaurant/date whose desired
-- window overlaps the slot that just opened up.
CREATE INDEX idx_waitlist_lookup ON waitlist_entries(restaurant_id, booking_date) WHERE status = 'ACTIVE';
CREATE INDEX idx_waitlist_user ON waitlist_entries(user_id, created_at DESC);

-- New notification type for "a table opened up" pings to waitlisted users.
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('BOOKING_CONFIRMED', 'BOOKING_REJECTED', 'BOOKING_CANCELLED', 'BOOKING_COMPLETED', 'WAITLIST_TABLE_AVAILABLE'));

ALTER TABLE notification_outbox DROP CONSTRAINT notification_outbox_type_check;
ALTER TABLE notification_outbox ADD CONSTRAINT notification_outbox_type_check
    CHECK (type IN ('BOOKING_CONFIRMED', 'BOOKING_REJECTED', 'BOOKING_CANCELLED', 'BOOKING_COMPLETED', 'WAITLIST_TABLE_AVAILABLE'));
