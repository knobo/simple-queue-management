-- Extend organizations with business info, location, and visibility settings

-- Business info
ALTER TABLE organizations ADD COLUMN description TEXT;
ALTER TABLE organizations ADD COLUMN website VARCHAR(500);
ALTER TABLE organizations ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE organizations ADD COLUMN phone VARCHAR(50);
ALTER TABLE organizations ADD COLUMN logo_url VARCHAR(500);

-- Location
ALTER TABLE organizations ADD COLUMN street_address VARCHAR(255);
ALTER TABLE organizations ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE organizations ADD COLUMN city VARCHAR(100);
ALTER TABLE organizations ADD COLUMN country VARCHAR(100) DEFAULT 'Norge';
ALTER TABLE organizations ADD COLUMN latitude DECIMAL(10,7);
ALTER TABLE organizations ADD COLUMN longitude DECIMAL(10,7);

-- Visibility flags (all true by default for backward compat)
ALTER TABLE organizations ADD COLUMN show_description BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organizations ADD COLUMN show_address BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organizations ADD COLUMN show_phone BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organizations ADD COLUMN show_email BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organizations ADD COLUMN show_website BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organizations ADD COLUMN show_hours BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organizations ADD COLUMN public_listed BOOLEAN NOT NULL DEFAULT FALSE;

-- Indexes for geo-search
CREATE INDEX idx_organizations_location ON organizations(latitude, longitude) 
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
CREATE INDEX idx_organizations_city ON organizations(city) WHERE city IS NOT NULL;
CREATE INDEX idx_organizations_public_listed ON organizations(public_listed) 
    WHERE public_listed = TRUE;
