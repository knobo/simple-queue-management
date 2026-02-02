-- Extend queues with queue-specific description and hints

ALTER TABLE queues ADD COLUMN description TEXT;
ALTER TABLE queues ADD COLUMN location_hint VARCHAR(255);
ALTER TABLE queues ADD COLUMN estimated_service_time_minutes INTEGER;
