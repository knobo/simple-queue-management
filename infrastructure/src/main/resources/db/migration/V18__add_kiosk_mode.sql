-- Add kiosk mode auto-close configuration to queues
-- auto_close_seconds: Number of seconds before ticket page auto-closes (0 = disabled, default 5)
ALTER TABLE queues ADD COLUMN auto_close_seconds INT NOT NULL DEFAULT 5;
