-- Add display_token column to queues table
ALTER TABLE queues ADD COLUMN display_token VARCHAR(64) UNIQUE;

-- Generate initial display tokens for existing queues
UPDATE queues SET display_token = md5(random()::text || clock_timestamp()::text || id::text) WHERE display_token IS NULL;
