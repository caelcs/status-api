-- Service Status Dashboard — baseline schema (PRD §4).
-- Status enum (per service): 'up' | 'degraded' | 'down' | 'unknown'

CREATE TABLE api_keys (
    id          UUID PRIMARY KEY,
    key         VARCHAR(64)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    env         VARCHAR(64)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    UNIQUE (env, key)
);

CREATE TABLE services (
    id                   UUID         PRIMARY KEY,
    key                  VARCHAR(64)  NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    env                  VARCHAR(64)  NOT NULL,
    description          TEXT,
    team                 VARCHAR(255),
    health_url           VARCHAR(2048) NOT NULL,
    tags                 JSONB,
    status               VARCHAR(16)  NOT NULL DEFAULT 'unknown',
    status_changed_at    TIMESTAMPTZ,
    latency_ms           INTEGER,
    last_checked_at      TIMESTAMPTZ,
    consecutive_failures INTEGER      NOT NULL DEFAULT 0,
    next_check_at        TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (env, key)
);

CREATE TABLE service_claim (
    service_id       UUID         PRIMARY KEY REFERENCES services(id) ON DELETE CASCADE,
    owner_instance   VARCHAR(255) NOT NULL,
    lease_expires_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE status_history (
    id           BIGSERIAL   PRIMARY KEY,
    service_id   UUID        NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    from_status  VARCHAR(16),
    to_status    VARCHAR(16) NOT NULL,
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason       VARCHAR(255)
);
CREATE INDEX idx_status_history_service_time ON status_history (service_id, changed_at DESC);

CREATE TABLE instance (
    id              VARCHAR(255) PRIMARY KEY,
    last_heartbeat  TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL
);
