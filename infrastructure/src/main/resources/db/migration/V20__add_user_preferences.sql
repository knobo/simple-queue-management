-- User preferences table for storing per-user settings like language preference
CREATE TABLE user_preferences (
    user_id VARCHAR(255) PRIMARY KEY,
    preferred_language VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index for faster lookups
CREATE INDEX idx_user_preferences_language ON user_preferences(preferred_language);

COMMENT ON TABLE user_preferences IS 'Stores per-user preferences like language settings';
COMMENT ON COLUMN user_preferences.preferred_language IS 'ISO language code (en, no, de, es, fr, it, ja, pl, pt)';
