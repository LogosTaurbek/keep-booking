-- V004: бронирования

-- Расширение для exclusion constraint (защита от двойного бронирования)
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE bookings (
    id               BIGSERIAL    PRIMARY KEY,
    restaurant_id    BIGINT       NOT NULL REFERENCES restaurants(id),
    table_id         BIGINT       NOT NULL REFERENCES restaurant_tables(id),
    user_id          BIGINT       NOT NULL REFERENCES users(id),
    booking_date     DATE         NOT NULL,
    time_from        TIME         NOT NULL,
    time_to          TIME         NOT NULL,
    guest_count      INTEGER      NOT NULL,
    comment          TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN (
                             'PENDING', 'CONFIRMED', 'REJECTED',
                             'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    source           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    idempotency_key  VARCHAR(255) UNIQUE,
    confirmed_by     BIGINT       REFERENCES users(id),
    cancelled_by     BIGINT       REFERENCES users(id),
    cancel_reason    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Exclusion constraint: запрет двойного бронирования одного стола
-- Блокирует пересечение интервалов для активных броней на уровне БД
ALTER TABLE bookings ADD CONSTRAINT no_double_booking
    EXCLUDE USING gist (
        table_id WITH =,
        tsrange(
            (booking_date + time_from)::TIMESTAMP,
            (booking_date + time_to)::TIMESTAMP
        ) WITH &&
    )
    WHERE (status IN ('PENDING', 'CONFIRMED'));

CREATE INDEX idx_bookings_user         ON bookings(user_id, status);
CREATE INDEX idx_bookings_restaurant   ON bookings(restaurant_id, booking_date, status);
CREATE INDEX idx_bookings_table_date   ON bookings(table_id, booking_date);
CREATE INDEX idx_bookings_idempotency  ON bookings(idempotency_key) WHERE idempotency_key IS NOT NULL;
