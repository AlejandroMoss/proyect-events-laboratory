CREATE TABLE callback_events (
    id UUID PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ(6) NOT NULL,
    received_at TIMESTAMPTZ(6) NOT NULL DEFAULT clock_timestamp(),
    payload JSONB NOT NULL,
    CONSTRAINT uq_callback_source_external_id UNIQUE (source, external_id),
    CONSTRAINT ck_callback_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_callback_received ON callback_events (received_at DESC, id DESC);
CREATE INDEX idx_callback_source_received ON callback_events (source, received_at DESC, id DESC);
CREATE INDEX idx_callback_type_received ON callback_events (event_type, received_at DESC, id DESC);
