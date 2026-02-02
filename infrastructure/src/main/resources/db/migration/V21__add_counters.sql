-- Add counters (service stations) for queues
CREATE TABLE counters (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    number INTEGER NOT NULL,
    name VARCHAR(255),
    current_operator_id VARCHAR(255),
    current_ticket_id UUID REFERENCES tickets(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(queue_id, number)
);

CREATE INDEX idx_counters_queue ON counters(queue_id);
CREATE INDEX idx_counters_operator ON counters(current_operator_id);

-- Add counter_id and served_by to tickets to track which counter served which ticket
ALTER TABLE tickets ADD COLUMN counter_id UUID REFERENCES counters(id) ON DELETE SET NULL;
ALTER TABLE tickets ADD COLUMN served_by VARCHAR(255);

CREATE INDEX idx_tickets_counter ON tickets(counter_id);
CREATE INDEX idx_tickets_served_by ON tickets(served_by);

-- Add max_counters_per_queue to tier_limits
ALTER TABLE tier_limits ADD COLUMN max_counters_per_queue INTEGER NOT NULL DEFAULT 1;

-- Update tier limits with counter defaults
UPDATE tier_limits SET max_counters_per_queue = 1 WHERE tier = 'FREE';
UPDATE tier_limits SET max_counters_per_queue = 3 WHERE tier = 'STARTER';
UPDATE tier_limits SET max_counters_per_queue = 10 WHERE tier = 'PRO';
UPDATE tier_limits SET max_counters_per_queue = -1 WHERE tier = 'ENTERPRISE';

-- Create a default counter for each existing queue
INSERT INTO counters (id, queue_id, number, name, current_operator_id, current_ticket_id, created_at, updated_at)
SELECT 
    gen_random_uuid(), 
    id, 
    1, 
    NULL, 
    NULL, 
    NULL, 
    NOW(), 
    NOW()
FROM queues;
