-- Opening hours per day of week for organizations

CREATE TABLE opening_hours (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),  -- 1=Monday, 7=Sunday (ISO)
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_opening_hours_org_day UNIQUE(organization_id, day_of_week)
);

CREATE INDEX idx_opening_hours_org ON opening_hours(organization_id);
