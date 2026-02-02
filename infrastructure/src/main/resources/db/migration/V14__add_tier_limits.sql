-- Tier limits configuration table
-- Allows superadmin to configure limits per subscription tier

CREATE TABLE tier_limits (
    tier VARCHAR(50) PRIMARY KEY,
    max_queues INTEGER NOT NULL,
    max_operators_per_queue INTEGER NOT NULL,
    max_tickets_per_day INTEGER NOT NULL,
    max_active_tickets INTEGER NOT NULL,
    max_invites_per_month INTEGER NOT NULL,
    can_use_email_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    can_use_custom_branding BOOLEAN NOT NULL DEFAULT FALSE,
    can_use_analytics BOOLEAN NOT NULL DEFAULT FALSE,
    can_use_api_access BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(255)
);

-- Seed default values matching current hardcoded limits
-- -1 represents unlimited
INSERT INTO tier_limits (tier, max_queues, max_operators_per_queue, max_tickets_per_day, max_active_tickets, max_invites_per_month, can_use_email_notifications, can_use_custom_branding, can_use_analytics, can_use_api_access, updated_at, updated_by) VALUES
    ('FREE', 1, 0, 50, 100, 5, FALSE, FALSE, FALSE, FALSE, NOW(), 'system'),
    ('STARTER', 3, 2, 200, 500, 20, TRUE, FALSE, FALSE, FALSE, NOW(), 'system'),
    ('PRO', 10, 10, -1, -1, -1, TRUE, TRUE, TRUE, FALSE, NOW(), 'system'),
    ('ENTERPRISE', -1, -1, -1, -1, -1, TRUE, TRUE, TRUE, TRUE, NOW(), 'system');

CREATE INDEX idx_tier_limits_updated_at ON tier_limits(updated_at);
