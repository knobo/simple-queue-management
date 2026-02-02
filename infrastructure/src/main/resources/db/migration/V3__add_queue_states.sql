CREATE TABLE queue_states (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    order_index INTEGER NOT NULL
);

-- Backfill default states for existing queues
-- We use a CTE or similar to generate UUIDs if possible, or just INSERT SELECT
-- Assuming pgcrypto or newer postgres is available for gen_random_uuid()

INSERT INTO queue_states (id, queue_id, name, status, order_index)
SELECT gen_random_uuid(), id, 'Waiting', 'WAITING', 1 FROM queues;

INSERT INTO queue_states (id, queue_id, name, status, order_index)
SELECT gen_random_uuid(), id, 'Serving', 'CALLED', 2 FROM queues;

INSERT INTO queue_states (id, queue_id, name, status, order_index)
SELECT gen_random_uuid(), id, 'Done', 'COMPLETED', 3 FROM queues;

INSERT INTO queue_states (id, queue_id, name, status, order_index)
SELECT gen_random_uuid(), id, 'Cancelled', 'CANCELLED', 4 FROM queues;

ALTER TABLE tickets ADD COLUMN state_id UUID REFERENCES queue_states(id);

UPDATE tickets t
SET state_id = qs.id
FROM queue_states qs
WHERE t.queue_id = qs.queue_id AND t.status = qs.status;
