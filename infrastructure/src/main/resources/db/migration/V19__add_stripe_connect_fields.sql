-- Add Stripe Connect fields to sellers table
-- Enables direct payouts to sellers via Stripe Express accounts

ALTER TABLE sellers 
    ADD COLUMN stripe_account_id VARCHAR(255),
    ADD COLUMN stripe_charges_enabled BOOLEAN DEFAULT FALSE,
    ADD COLUMN stripe_payouts_enabled BOOLEAN DEFAULT FALSE,
    ADD COLUMN stripe_onboarding_completed BOOLEAN DEFAULT FALSE;

CREATE INDEX idx_sellers_stripe_account_id ON sellers(stripe_account_id);

-- Track Stripe Connect transfers separately for auditing
CREATE TABLE stripe_connect_transfers (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES sellers(id),
    payout_id UUID REFERENCES seller_payouts(id),
    
    stripe_transfer_id VARCHAR(255) NOT NULL UNIQUE,
    stripe_account_id VARCHAR(255) NOT NULL,
    
    amount BIGINT NOT NULL,  -- Amount in cents
    currency VARCHAR(3) NOT NULL DEFAULT 'NOK',
    description TEXT,
    
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, PAID, FAILED
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stripe_transfers_seller ON stripe_connect_transfers(seller_id);
CREATE INDEX idx_stripe_transfers_stripe_id ON stripe_connect_transfers(stripe_transfer_id);
