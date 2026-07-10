-- V001: справочники (страны, города, кухни)

CREATE TABLE countries (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(2)   NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE cities (
    id         BIGSERIAL    PRIMARY KEY,
    country_id BIGINT       NOT NULL REFERENCES countries(id),
    name       VARCHAR(100) NOT NULL,
    latitude   DECIMAL(9,6),
    longitude  DECIMAL(9,6),
    timezone   VARCHAR(50)  NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE cuisines (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed data
INSERT INTO countries (name, code) VALUES
    ('Kazakhstan', 'KZ'),
    ('Russia',     'RU'),
    ('Uzbekistan', 'UZ');

INSERT INTO cities (country_id, name, latitude, longitude, timezone) VALUES
    (1, 'Almaty',   43.222000, 76.851200, 'Asia/Almaty'),
    (1, 'Astana',   51.180100, 71.446000, 'Asia/Almaty'),
    (2, 'Moscow',   55.755800, 37.617300, 'Europe/Moscow'),
    (3, 'Tashkent', 41.299500, 69.240100, 'Asia/Tashkent');

INSERT INTO cuisines (name, slug) VALUES
    ('Italian',  'italian'),
    ('Kazakh',   'kazakh'),
    ('Japanese', 'japanese'),
    ('Chinese',  'chinese'),
    ('American', 'american'),
    ('Indian',   'indian'),
    ('Uzbek',    'uzbek'),
    ('Georgian', 'georgian'),
    ('Turkish',  'turkish');
