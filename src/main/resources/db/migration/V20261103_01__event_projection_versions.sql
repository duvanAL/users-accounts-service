CREATE TABLE IF NOT EXISTS accounts.event_outbox_versions (
  entity_key VARCHAR(300) PRIMARY KEY,
  version BIGINT NOT NULL CHECK (version > 0)
);
