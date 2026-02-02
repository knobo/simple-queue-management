CREATE TABLE queue_members (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    invited_by VARCHAR(255),
    UNIQUE(queue_id, user_id)
);

CREATE TABLE invites (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    accepted_by_user_id VARCHAR(255)
);

CREATE INDEX idx_invites_token ON invites(token);
CREATE INDEX idx_invites_queue_status ON invites(queue_id, status);
CREATE INDEX idx_queue_members_user ON queue_members(user_id);
