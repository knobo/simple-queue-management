-- Add counter sessions to track which operator is working at which counter
CREATE TABLE counter_sessions (
    id UUID PRIMARY KEY,
    counter_id UUID NOT NULL REFERENCES counters(id) ON DELETE CASCADE,
    operator_id VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP
);

-- Only one active session per operator (ended_at IS NULL means active)
-- PostgreSQL partial unique index syntax
CREATE UNIQUE INDEX idx_unique_active_session_per_operator 
    ON counter_sessions(operator_id) 
    WHERE ended_at IS NULL;

CREATE INDEX idx_counter_sessions_counter ON counter_sessions(counter_id);
CREATE INDEX idx_counter_sessions_operator ON counter_sessions(operator_id);
CREATE INDEX idx_counter_sessions_active ON counter_sessions(counter_id) WHERE ended_at IS NULL;
