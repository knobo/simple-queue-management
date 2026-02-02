-- Sales/Commission System
-- Enables sellers to earn commission on referred customers

-- Selgere med konfigurerbare vilkår
CREATE TABLE sellers (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    referral_code VARCHAR(50) NOT NULL UNIQUE,
    
    -- Provisjonsvilkår (konfigurerbare per selger)
    commission_percent DECIMAL(5,2) NOT NULL DEFAULT 20.00,
    commission_cap_per_customer DECIMAL(10,2),  -- Maks per kunde (null = ingen tak)
    commission_period_months INTEGER NOT NULL DEFAULT 12,
    
    -- Krav for å beholde selgerstatus
    min_sales_to_maintain INTEGER NOT NULL DEFAULT 5,
    status_period_months INTEGER NOT NULL DEFAULT 6,  -- Må selge X i løpet av N mnd
    
    -- Utbetalingsmetode
    payout_method VARCHAR(50) NOT NULL DEFAULT 'MANUAL',  -- MANUAL, STRIPE_CONNECT, VIPPS, BANK_TRANSFER
    payout_details JSONB,  -- Stripe account id, Vipps nummer, kontonummer, etc.
    
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, SUSPENDED
    status_valid_until TIMESTAMP,  -- Når selgerstatus utløper
    
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,  -- Superadmin som opprettet
    updated_at TIMESTAMP NOT NULL
);

-- Bedriftskontoer (opprettet av selgere)
CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    org_number VARCHAR(50),  -- Organisasjonsnummer
    
    -- Hvem opprettet og hvem referred
    created_by_seller_id UUID REFERENCES sellers(id),
    admin_email VARCHAR(255) NOT NULL,  -- Blir invitert som admin
    admin_user_id VARCHAR(255),  -- Fylles inn når admin aksepterer
    
    -- Abonnement
    subscription_id UUID REFERENCES subscriptions(id),
    
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, ACTIVE, SUSPENDED
    created_at TIMESTAMP NOT NULL,
    activated_at TIMESTAMP
);

-- Selger-referanser (kan være individ eller organisasjon)
CREATE TABLE seller_referrals (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES sellers(id),
    
    -- Enten bruker ELLER organisasjon
    customer_user_id VARCHAR(255),
    organization_id UUID REFERENCES organizations(id),
    
    subscription_id UUID REFERENCES subscriptions(id),
    referred_at TIMESTAMP NOT NULL,
    commission_ends_at TIMESTAMP NOT NULL,
    total_commission_earned DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_commission_paid DECIMAL(10,2) NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_referral_target CHECK (
        (customer_user_id IS NOT NULL AND organization_id IS NULL) OR
        (customer_user_id IS NULL AND organization_id IS NOT NULL)
    )
);

-- Provisjonshistorikk
CREATE TABLE commission_entries (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES sellers(id),
    referral_id UUID NOT NULL REFERENCES seller_referrals(id),
    
    -- Hva utløste provisjonen
    source_type VARCHAR(50) NOT NULL,  -- SUBSCRIPTION_PAYMENT, UPGRADE, etc.
    source_reference VARCHAR(255),  -- Stripe invoice id etc.
    
    gross_amount DECIMAL(10,2) NOT NULL,  -- Kundens betaling
    commission_percent DECIMAL(5,2) NOT NULL,
    commission_amount DECIMAL(10,2) NOT NULL,
    
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Utbetalinger til selgere
CREATE TABLE seller_payouts (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES sellers(id),
    
    amount DECIMAL(10,2) NOT NULL,
    payout_method VARCHAR(50) NOT NULL,
    payout_reference VARCHAR(255),  -- Stripe transfer id, Vipps ref, etc.
    
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    
    -- Hvilke commission_entries som er inkludert
    entries_from DATE NOT NULL,
    entries_to DATE NOT NULL,
    
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    completed_at TIMESTAMP,
    notes TEXT
);

-- Kobling mellom utbetaling og entries
CREATE TABLE payout_entries (
    payout_id UUID NOT NULL REFERENCES seller_payouts(id),
    entry_id UUID NOT NULL REFERENCES commission_entries(id),
    PRIMARY KEY (payout_id, entry_id)
);

-- Selger aktivitetslogg (for status-beregning)
CREATE TABLE seller_activity_log (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES sellers(id),
    activity_type VARCHAR(50) NOT NULL,  -- SALE, STATUS_RENEWED, STATUS_EXPIRED
    referral_id UUID REFERENCES seller_referrals(id),
    created_at TIMESTAMP NOT NULL,
    details JSONB
);

-- Koble køer til organisasjoner
ALTER TABLE queues ADD COLUMN organization_id UUID REFERENCES organizations(id);

-- Indexes
CREATE INDEX idx_sellers_referral_code ON sellers(referral_code);
CREATE INDEX idx_sellers_status ON sellers(status);
CREATE INDEX idx_sellers_user_id ON sellers(user_id);
CREATE INDEX idx_referrals_seller ON seller_referrals(seller_id);
CREATE INDEX idx_referrals_customer ON seller_referrals(customer_user_id);
CREATE INDEX idx_referrals_organization ON seller_referrals(organization_id);
CREATE INDEX idx_organizations_seller ON organizations(created_by_seller_id);
CREATE INDEX idx_organizations_admin ON organizations(admin_user_id);
CREATE INDEX idx_commission_entries_seller ON commission_entries(seller_id);
CREATE INDEX idx_commission_entries_referral ON commission_entries(referral_id);
CREATE INDEX idx_seller_payouts_seller ON seller_payouts(seller_id);
CREATE INDEX idx_seller_payouts_status ON seller_payouts(status);
CREATE INDEX idx_seller_activity_seller ON seller_activity_log(seller_id);
CREATE INDEX idx_queues_organization ON queues(organization_id);
