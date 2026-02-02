CREATE TABLE queues (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    open BOOLEAN NOT NULL DEFAULT FALSE,
    qr_code_secret VARCHAR(255) NOT NULL,
    qr_code_type VARCHAR(50) NOT NULL,
    last_rotated_at TIMESTAMP NOT NULL
);

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id),
    number INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    ntfy_topic VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    called_at TIMESTAMP,
    completed_at TIMESTAMP,
    UNIQUE(queue_id, number)
);

CREATE INDEX idx_tickets_queue_status ON tickets(queue_id, status);
