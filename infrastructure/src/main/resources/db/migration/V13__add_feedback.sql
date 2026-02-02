-- Feedback system migration
-- Allows users to submit bug reports, feature requests, and general feedback

CREATE TABLE feedback (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,          -- Keycloak subject
    user_email VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,              -- BUG, FEATURE, GENERAL
    category VARCHAR(50) NOT NULL,          -- QUEUE_MANAGEMENT, NOTIFICATIONS, etc.
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    screenshot_url VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',  -- NEW, IN_PROGRESS, RESOLVED, CLOSED
    
    -- Context captured at submission time
    user_agent TEXT,
    current_url VARCHAR(500),
    subscription_tier VARCHAR(50),
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for common queries
CREATE INDEX idx_feedback_user_id ON feedback(user_id);
CREATE INDEX idx_feedback_type ON feedback(type);
CREATE INDEX idx_feedback_status ON feedback(status);
CREATE INDEX idx_feedback_category ON feedback(category);
CREATE INDEX idx_feedback_created_at ON feedback(created_at DESC);

-- Admin notes table for internal comments on feedback
CREATE TABLE feedback_notes (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL REFERENCES feedback(id) ON DELETE CASCADE,
    admin_user_id VARCHAR(255) NOT NULL,
    note TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feedback_notes_feedback_id ON feedback_notes(feedback_id);
