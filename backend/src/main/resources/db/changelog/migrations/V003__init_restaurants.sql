-- V003: компании, рестораны, залы, столики

CREATE TABLE companies (
    id            BIGSERIAL    PRIMARY KEY,
    owner_user_id BIGINT       NOT NULL REFERENCES users(id),
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    logo_url      VARCHAR(500),
    website       VARCHAR(255),
    phone         VARCHAR(20),
    email         VARCHAR(255),
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING_MODERATION'
                      CHECK (status IN ('PENDING_MODERATION', 'ACTIVE', 'BLOCKED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE restaurants (
    id            BIGSERIAL      PRIMARY KEY,
    company_id    BIGINT         NOT NULL REFERENCES companies(id),
    name          VARCHAR(255)   NOT NULL,
    description   TEXT,
    address       VARCHAR(500),
    city_id       BIGINT         REFERENCES cities(id),
    latitude      DECIMAL(9,6),
    longitude     DECIMAL(9,6),
    timezone      VARCHAR(50)    NOT NULL DEFAULT 'UTC',
    rating        DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
    reviews_count INTEGER        NOT NULL DEFAULT 0,
    avg_check     INTEGER,
    status        VARCHAR(30)    NOT NULL DEFAULT 'DRAFT'
                      CHECK (status IN ('DRAFT', 'PENDING_MODERATION', 'ACTIVE', 'HIDDEN', 'BLOCKED')),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE restaurant_cuisines (
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    cuisine_id    BIGINT NOT NULL REFERENCES cuisines(id),
    PRIMARY KEY (restaurant_id, cuisine_id)
);

CREATE TABLE restaurant_working_hours (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT    NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    day_of_week   SMALLINT  NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    open_time     TIME,
    close_time    TIME,
    is_day_off    BOOLEAN   NOT NULL DEFAULT FALSE,
    UNIQUE (restaurant_id, day_of_week)
);

CREATE TABLE halls (
    id            BIGSERIAL    PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    floor         INTEGER      NOT NULL DEFAULT 1,
    canvas_width  INTEGER      NOT NULL DEFAULT 800,
    canvas_height INTEGER      NOT NULL DEFAULT 600,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE restaurant_tables (
    id           BIGSERIAL    PRIMARY KEY,
    hall_id      BIGINT       NOT NULL REFERENCES halls(id) ON DELETE CASCADE,
    number       VARCHAR(20)  NOT NULL,
    capacity     INTEGER      NOT NULL,
    min_capacity INTEGER,
    shape        VARCHAR(20)  NOT NULL DEFAULT 'RECT',
    type         VARCHAR(20)  NOT NULL DEFAULT 'REGULAR'
                     CHECK (type IN ('REGULAR', 'SOFA', 'VIP', 'BAR', 'TERRACE')),
    pos_x        INTEGER      NOT NULL DEFAULT 0,
    pos_y        INTEGER      NOT NULL DEFAULT 0,
    width        INTEGER      NOT NULL DEFAULT 80,
    height       INTEGER      NOT NULL DEFAULT 80,
    rotation     INTEGER      NOT NULL DEFAULT 0,
    is_vip       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_sofa      BOOLEAN      NOT NULL DEFAULT FALSE,
    near_window  BOOLEAN      NOT NULL DEFAULT FALSE,
    has_socket   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_smoking   BOOLEAN      NOT NULL DEFAULT FALSE,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (hall_id, number)
);

CREATE INDEX idx_restaurants_city_status ON restaurants(city_id, status);
CREATE INDEX idx_restaurants_company     ON restaurants(company_id);
CREATE INDEX idx_halls_restaurant        ON halls(restaurant_id);
CREATE INDEX idx_tables_hall             ON restaurant_tables(hall_id, status);
