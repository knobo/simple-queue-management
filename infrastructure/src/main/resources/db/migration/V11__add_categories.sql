-- Service categories for organization discovery

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    name_no VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    icon VARCHAR(50),
    parent_id UUID REFERENCES categories(id),
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE organization_categories (
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (organization_id, category_id)
);

CREATE INDEX idx_categories_parent ON categories(parent_id);
CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_org_categories_org ON organization_categories(organization_id);
CREATE INDEX idx_org_categories_cat ON organization_categories(category_id);

-- Seed initial categories
INSERT INTO categories (id, slug, name_no, name_en, icon, parent_id, sort_order) VALUES
    -- Top-level categories
    ('a1000000-0000-0000-0000-000000000001', 'healthcare', 'Helse', 'Healthcare', 'heart-pulse', NULL, 1),
    ('a2000000-0000-0000-0000-000000000001', 'government', 'Offentlig', 'Government', 'building-columns', NULL, 2),
    ('a3000000-0000-0000-0000-000000000001', 'services', 'Tjenester', 'Services', 'briefcase', NULL, 3),
    ('a4000000-0000-0000-0000-000000000001', 'retail', 'Butikk', 'Retail', 'shopping-cart', NULL, 4),
    
    -- Healthcare subcategories
    ('b1000000-0000-0000-0000-000000000001', 'doctor', 'Lege', 'Doctor', 'stethoscope', 'a1000000-0000-0000-0000-000000000001', 1),
    ('b1000000-0000-0000-0000-000000000002', 'dentist', 'Tannlege', 'Dentist', 'tooth', 'a1000000-0000-0000-0000-000000000001', 2),
    ('b1000000-0000-0000-0000-000000000003', 'pharmacy', 'Apotek', 'Pharmacy', 'prescription', 'a1000000-0000-0000-0000-000000000001', 3),
    ('b1000000-0000-0000-0000-000000000004', 'hospital', 'Sykehus', 'Hospital', 'hospital', 'a1000000-0000-0000-0000-000000000001', 4),
    ('b1000000-0000-0000-0000-000000000005', 'physio', 'Fysioterapi', 'Physiotherapy', 'person-walking', 'a1000000-0000-0000-0000-000000000001', 5),
    
    -- Government subcategories
    ('b2000000-0000-0000-0000-000000000001', 'nav', 'NAV', 'NAV', 'building', 'a2000000-0000-0000-0000-000000000001', 1),
    ('b2000000-0000-0000-0000-000000000002', 'police', 'Politi', 'Police', 'shield', 'a2000000-0000-0000-0000-000000000001', 2),
    ('b2000000-0000-0000-0000-000000000003', 'tax', 'Skatteetaten', 'Tax Office', 'file-invoice', 'a2000000-0000-0000-0000-000000000001', 3),
    ('b2000000-0000-0000-0000-000000000004', 'dmv', 'Trafikkstasjon', 'DMV', 'car', 'a2000000-0000-0000-0000-000000000001', 4),
    ('b2000000-0000-0000-0000-000000000005', 'municipality', 'Kommune', 'Municipality', 'landmark', 'a2000000-0000-0000-0000-000000000001', 5),
    
    -- Services subcategories
    ('b3000000-0000-0000-0000-000000000001', 'bank', 'Bank', 'Bank', 'university', 'a3000000-0000-0000-0000-000000000001', 1),
    ('b3000000-0000-0000-0000-000000000002', 'post', 'Post', 'Post Office', 'envelope', 'a3000000-0000-0000-0000-000000000001', 2),
    ('b3000000-0000-0000-0000-000000000003', 'telecom', 'Telecom', 'Telecom', 'phone', 'a3000000-0000-0000-0000-000000000001', 3),
    ('b3000000-0000-0000-0000-000000000004', 'insurance', 'Forsikring', 'Insurance', 'shield-check', 'a3000000-0000-0000-0000-000000000001', 4),
    
    -- Retail subcategories
    ('b4000000-0000-0000-0000-000000000001', 'electronics', 'Elektronikk', 'Electronics', 'laptop', 'a4000000-0000-0000-0000-000000000001', 1),
    ('b4000000-0000-0000-0000-000000000002', 'service-counter', 'Kundeservice', 'Service Counter', 'headset', 'a4000000-0000-0000-0000-000000000001', 2),
    ('b4000000-0000-0000-0000-000000000003', 'food', 'Dagligvare', 'Grocery', 'basket-shopping', 'a4000000-0000-0000-0000-000000000001', 3);
