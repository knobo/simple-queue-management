-- Add access token configuration to queues
-- Modes: 'static' (legacy/unchanged), 'rotating', 'one_time', 'time_limited'
ALTER TABLE queues ADD COLUMN access_token_mode VARCHAR(20) DEFAULT 'static';
ALTER TABLE queues ADD COLUMN token_rotation_minutes INT DEFAULT 0;
ALTER TABLE queues ADD COLUMN token_expiry_minutes INT DEFAULT 60;
ALTER TABLE queues ADD COLUMN token_max_uses INT; -- null = unlimited

-- Access tokens table
CREATE TABLE queue_access_tokens (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    token VARCHAR(32) UNIQUE NOT NULL,
    expires_at TIMESTAMP,
    max_uses INT,
    use_count INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_queue_access_tokens_token ON queue_access_tokens(token);
CREATE INDEX idx_queue_access_tokens_queue ON queue_access_tokens(queue_id);
CREATE INDEX idx_queue_access_tokens_active ON queue_access_tokens(queue_id, is_active) WHERE is_active = true;

-- Generate initial tokens for existing queues that use dynamic mode
-- For now, all existing queues stay in 'static' mode (backwards compatible)
-- No initial tokens needed since static mode uses qr_code_secret directly
