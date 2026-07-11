-- V013: audit log для важных действий (сейчас — переходы статуса брони)

CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    actor_id    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(60)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    details     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_actor  ON audit_log(actor_id);
