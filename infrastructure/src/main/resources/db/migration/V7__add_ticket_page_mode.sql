-- Add ticket_page_mode column to queues table
-- This configures how the ticket page displays: QR code only, button only, or both
ALTER TABLE queues ADD COLUMN ticket_page_mode VARCHAR(20) NOT NULL DEFAULT 'BOTH';
