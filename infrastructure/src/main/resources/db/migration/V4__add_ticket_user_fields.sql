-- Add user association fields to tickets table
ALTER TABLE tickets ADD COLUMN user_id VARCHAR(255);
ALTER TABLE tickets ADD COLUMN guest_email VARCHAR(255);
