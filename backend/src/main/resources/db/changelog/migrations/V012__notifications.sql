-- V012: in-app уведомления

CREATE TABLE notifications (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(40)  NOT NULL
                    CHECK (type IN (
                        'BOOKING_CONFIRMED', 'BOOKING_REJECTED',
                        'BOOKING_CANCELLED', 'BOOKING_COMPLETED')),
    title       VARCHAR(255) NOT NULL,
    message     TEXT         NOT NULL,
    booking_id  BIGINT       REFERENCES bookings(id) ON DELETE SET NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE is_read = FALSE;
