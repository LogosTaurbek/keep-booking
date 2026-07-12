-- V016: transactional outbox for push-notification delivery (tz2.txt §14)
-- Written in the same transaction as the business event that triggers a notification;
-- a separate scheduled worker (NotificationOutboxWorker) polls PENDING rows and delivers them.

CREATE TABLE notification_outbox (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    booking_id      BIGINT       REFERENCES bookings(id) ON DELETE SET NULL,
    type            VARCHAR(40)  NOT NULL
                        CHECK (type IN (
                            'BOOKING_CONFIRMED', 'BOOKING_REJECTED',
                            'BOOKING_CANCELLED', 'BOOKING_COMPLETED')),
    title           VARCHAR(255) NOT NULL,
    message         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'SENT', 'DEAD_LETTER')),
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- The worker only ever queries PENDING rows due for another attempt, ordered oldest-first.
CREATE INDEX idx_notification_outbox_pending ON notification_outbox(next_attempt_at) WHERE status = 'PENDING';
