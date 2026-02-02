-- Queue-specific opening hours (overrides organization defaults)

CREATE TABLE queue_opening_hours (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),  -- 0=Monday, 6=Sunday
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_queue_opening_hours_queue_day UNIQUE(queue_id, day_of_week)
);

CREATE INDEX idx_queue_opening_hours_queue ON queue_opening_hours(queue_id);

-- Queue closed dates (holidays, special closures)

CREATE TABLE queue_closed_dates (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    closed_date DATE NOT NULL,
    reason VARCHAR(255),
    CONSTRAINT uq_queue_closed_dates_queue_date UNIQUE(queue_id, closed_date)
);

CREATE INDEX idx_queue_closed_dates_queue ON queue_closed_dates(queue_id);
CREATE INDEX idx_queue_closed_dates_date ON queue_closed_dates(closed_date);
