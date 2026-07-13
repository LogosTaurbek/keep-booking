ALTER TABLE reviews
    ADD COLUMN owner_reply    TEXT,
    ADD COLUMN owner_reply_at TIMESTAMPTZ;
