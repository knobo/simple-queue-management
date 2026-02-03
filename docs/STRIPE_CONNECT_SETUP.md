# Stripe Connect Setup Guide for Simple Queue

This guide walks you through setting up Stripe Connect for Simple Queue, enabling businesses to accept payments and receive payouts directly to their bank accounts.

---

## 1. Prerequisites

Before you begin, ensure you have:

### Stripe Account Requirements
- [ ] **Stripe account** created at [stripe.com](https://stripe.com)
- [ ] **Verified email address** on your Stripe account
- [ ] Access to Stripe Dashboard (test mode is fine for development)

### Technical Requirements
- [ ] Stripe CLI installed locally (`brew install stripe/stripe-cli/stripe` on macOS)
- [ ] Access to your Simple Queue backend environment
- [ ] Ability to modify environment variables in your deployment
- [ ] HTTPS endpoint available (for webhooks) — use ngrok for local development

### Business Requirements (for production)
- [ ] Business verification documents ready (if activating live mode)
- [ ] Bank account for your platform (for collecting fees)
- [ ] Understanding of your fee structure (application fee %)

---

## 2. Stripe Dashboard Configuration

### 2.1 Enable Connect in Test Mode

1. **Log into Stripe Dashboard**
   - Go to [dashboard.stripe.com](https://dashboard.stripe.com)
   - Ensure you're in **Test mode** (toggle in top right)

2. **Navigate to Connect Settings**
   - Click **Settings** (gear icon, bottom left)
   - Under **Connect**, click **Settings**
   - Or go directly to: `Settings → Connect → Settings`

3. **Enable Connect**
   - Look for **"Get started with Connect"** button
   - Click it to enable Connect for your account
   - Fill out the platform profile:
     - **Business name**: Your platform name (e.g., "Simple Queue")
     - **Website URL**: Your platform's URL
     - **Business description**: Brief description of your service
     - **Integration type**: Select "Standard" or "Express" (we use Express)

4. **Verify Connect is Active**
   - You should see the Connect dashboard at `Payments → Connect`
   - If you see "You can only create new accounts if you've signed up for Connect", see [Common Errors](#5-common-errors-and-solutions)

### 2.2 Enable Express Accounts

Express accounts are the fastest way to onboard businesses — Stripe handles the UI and verification.

1. **Go to Connect Settings**
   - `Settings → Connect → Settings`

2. **Enable Express**
   - Under **Account types**, ensure **Express** is enabled
   - If not, click **Enable** next to Express

3. **Set Capabilities**
   - In the same section, set default capabilities:
     - ✅ **Transfers** (required for payouts)
     - ✅ **Card payments** (required for accepting payments)
   - These are usually enabled by default for Express accounts

### 2.3 Set Up Branding for Express

Customize the onboarding experience to match your brand:

1. **Navigate to Branding Settings**
   - `Settings → Connect → Branding`

2. **Upload Brand Assets**
   
   | Asset | Specifications | Recommended |
   |-------|----------------|-------------|
   | **Logo** | Square, 128x128px minimum | PNG or SVG |
   | **Icon** | Square, 32x32px | Favicon style |
   | **Brand color** | Hex color code | Your primary brand color |

3. **Configure Display Settings**
   - **Business name**: Display name shown to connected accounts
   - **Accent color**: Used for buttons and links during onboarding
   - **Privacy policy URL**: Required — link to your privacy policy
   - **Terms of service URL**: Required — link to your terms

4. **Save Changes**
   - Click **Save** at the bottom of the page

### 2.4 Configure Webhook Endpoints

Webhooks notify your application of events (payments, payouts, account updates).

#### 2.4.1 For Local Development (with ngrok)

1. **Install ngrok** if not already installed:
   ```bash
   # macOS
   brew install ngrok
   
   # Or download from https://ngrok.com/download
   ```

2. **Start ngrok** to expose your local server:
   ```bash
   ngrok http 3000  # or your backend port
   ```
   - Copy the HTTPS URL (e.g., `https://abc123.ngrok.io`)

#### 2.4.2 Create Webhook Endpoint in Stripe Dashboard

1. **Go to Webhooks Settings**
   - `Developers → Webhooks`
   - Or directly: [dashboard.stripe.com/webhooks](https://dashboard.stripe.com/webhooks)

2. **Add Endpoint**
   - Click **"Add endpoint"**
   - **Endpoint URL**: `https://your-domain.com/api/stripe/webhooks`
     - For local: `https://abc123.ngrok.io/api/stripe/webhooks`
   - **Listen to**: Select "Select events" (not "All events")

3. **Select Required Events**
   
   **Payment Events:**
   - ✅ `payment_intent.succeeded`
   - ✅ `payment_intent.payment_failed`
   - ✅ `payment_intent.canceled`
   - ✅ `charge.succeeded`
   - ✅ `charge.failed`
   - ✅ `charge.refunded`
   - ✅ `charge.dispute.created`
   
   **Connect Account Events:**
   - ✅ `account.updated`
   - ✅ `account.application.deauthorized`
   - ✅ `capability.updated`
   - ✅ `person.updated`
   
   **Payout Events:**
   - ✅ `payout.created`
   - ✅ `payout.paid`
   - ✅ `payout.failed`
   - ✅ `payout.canceled`
   
   **Transfer Events:**
   - ✅ `transfer.created`
   - ✅ `transfer.paid`
   - ✅ `transfer.failed`

4. **Save Endpoint**
   - Click **Add endpoint**

5. **Get Webhook Secret**
   - Click on your newly created endpoint
   - Click **"Reveal"** next to **Signing secret**
   - Copy the secret (starts with `whsec_`)
   - Save this for your environment variables

#### 2.4.3 Separate Connect Webhook (Optional but Recommended)

For production, create a separate webhook endpoint specifically for Connect events:

1. **Add Another Endpoint**
   - Same steps as above
   - **Endpoint URL**: `https://your-domain.com/api/stripe/connect-webhooks`
   - **Connect**: Toggle "Connect" to receive events on behalf of connected accounts

2. **Select Connect-Specific Events:**
   - ✅ `account.updated`
   - ✅ `capability.updated`
   - ✅ `person.updated`
   - ✅ `payout.*` events

---

## 3. Environment Variables

Add these to your Simple Queue backend `.env` file:

```bash
# =============================================================================
# Stripe Configuration
# =============================================================================

# Your Stripe API key
# - Use sk_test_... for development
# - Use sk_live_... for production
STRIPE_API_KEY=sk_test_...

# Webhook secret from your main webhook endpoint
# Found at: Developers → Webhooks → [Your endpoint] → Signing secret
STRIPE_WEBHOOK_SECRET=whsec_...

# (Optional) Separate webhook secret for Connect events
# Use this if you created a separate Connect webhook endpoint
STRIPE_CONNECT_WEBHOOK_SECRET=whsec_...

# Your Stripe Publishable Key (for frontend)
# pk_test_... for development, pk_live_... for production
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_test_...

# =============================================================================
# Stripe Connect Platform Settings
# =============================================================================

# Your platform's Stripe Account ID
# Found at: Settings → Account settings → Account details
# Format: acct_...
STRIPE_PLATFORM_ACCOUNT_ID=acct_...

# Application fee percentage (0-99, whole number)
# This is your platform's fee on each transaction
# Example: 5 = 5% fee
STRIPE_APPLICATION_FEE_PERCENT=5

# =============================================================================
# Optional: Stripe Connect Express Branding
# =============================================================================

# URL users are redirected to after completing Express onboarding
STRIPE_CONNECT_ONBOARDING_RETURN_URL=https://your-app.com/dashboard/business

# URL users are redirected to if they refresh the onboarding page
STRIPE_CONNECT_ONBOARDING_REFRESH_URL=https://your-app.com/dashboard/business/onboarding
```

### Where to Find Each Value

| Variable | Location in Stripe Dashboard |
|----------|------------------------------|
| `STRIPE_API_KEY` | `Developers → API keys → Secret key` |
| `STRIPE_WEBHOOK_SECRET` | `Developers → Webhooks → [Endpoint] → Signing secret` |
| `STRIPE_PLATFORM_ACCOUNT_ID` | `Settings → Account settings → Account details` |
| `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` | `Developers → API keys → Publishable key` |

---

## 4. Testing with CLI

The Stripe CLI makes testing webhooks and simulating events easy.

### 4.1 Install and Login

```bash
# Install Stripe CLI (macOS)
brew install stripe/stripe-cli/stripe

# Login to your Stripe account
stripe login

# Follow the browser prompt to authorize
```

### 4.2 Forward Webhooks Locally

Instead of using ngrok manually, let Stripe CLI forward webhooks:

```bash
# Forward webhooks to your local server
stripe listen --forward-to localhost:3000/api/stripe/webhooks

# Or for a specific port
stripe listen --forward-to http://localhost:3000/api/stripe/webhooks
```

**Output will show:**
```
> Ready! You are using Stripe API Version [2024-01-01]
> Your webhook signing secret is whsec_xxxxxxxxxxxxxxxxxxx (^C to quit)
```

**Copy this webhook secret** and use it as `STRIPE_WEBHOOK_SECRET` in your `.env`.

### 4.3 Create Test Connected Accounts

```bash
# Create an Express test account
stripe accounts create --type=express

# Create with capabilities pre-set
stripe accounts create \
  --type=express \
  -d "capabilities[transfers][requested]=true" \
  -d "capabilities[card_payments][requested]=true"
```

**Example response:**
```json
{
  "id": "acct_1XXXXXXXXXXXXXXX",
  "type": "express",
  "object": "account"
}
```

### 4.4 Trigger Webhook Events

```bash
# Trigger a payment intent succeeded event
stripe trigger payment_intent.succeeded

# Trigger with Connect (includes account info)
stripe trigger --stripe-account=acct_1XXXXXXXXXXXXXXX payment_intent.succeeded

# Trigger account updated event
stripe trigger account.updated

# Trigger payout events
stripe trigger payout.created
stripe trigger payout.paid

# Trigger a charge dispute (for testing dispute handling)
stripe trigger charge.dispute.created
```

### 4.5 Simulate Payments

```bash
# Create a payment intent (as platform)
stripe payment_intents create \
  --amount=2000 \
  --currency=usd \
  -d "application_fee_amount=100" \
  -d "transfer_data[destination]=acct_1XXXXXXXXXXXXXXX"

# Create a customer
stripe customers create \
  --email=test@example.com \
  --name="Test Customer"

# Create a payment method (using test card)
stripe payment_methods create \
  --type=card \
  -d "card[number]=4242424242424242" \
  -d "card[exp_month]=12" \
  -d "card[exp_year]=2030" \
  -d "card[cvc]=123"
```

### 4.6 Test Onboarding Flow

```bash
# Generate an account link for Express onboarding
stripe account_links create \
  --account=acct_1XXXXXXXXXXXXXXX \
  --refresh_url=https://example.com/reauth \
  --return_url=https://example.com/return \
  --type=account_onboarding

# The response contains a URL — open it in browser to test onboarding
```

### 4.7 Useful Stripe CLI Commands

```bash
# List recent events
stripe events list --limit=10

# Get specific event details
stripe events retrieve evt_1XXXXXXXXXXXXXXX

# List connected accounts
stripe accounts list

# Get account details
stripe accounts retrieve acct_1XXXXXXXXXXXXXXX

# Open Stripe Dashboard for a specific resource
stripe open https://dashboard.stripe.com/acct_1XXXXXXXXXXXXXXX/test/dashboard
```

---

## 5. Common Errors and Solutions

### Error: "You can only create new accounts if you've signed up for Connect"

**Cause:** Connect is not enabled on your Stripe account.

**Solution:**
1. Go to `Settings → Connect → Settings`
2. Click **"Get started"** or **"Activate Connect"**
3. Complete the platform profile form
4. Wait for Stripe to review (usually instant for test mode)
5. For live mode, you'll need to provide additional business documentation

---

### Error: "No such account: 'acct_...'"

**Cause:** You're trying to access a connected account with the wrong API key, or the account doesn't exist.

**Solution:**
1. Verify the account ID is correct
2. Ensure you're using the **platform's** secret key (`sk_test_...`), not the connected account's
3. For Express accounts, always use your platform's API key with the `Stripe-Account` header

```javascript
// Correct way to make API calls on behalf of connected account
const stripe = require('stripe')(process.env.STRIPE_API_KEY);

await stripe.paymentIntents.create({
  amount: 1000,
  currency: 'usd',
}, {
  stripeAccount: 'acct_1XXXXXXXXXXXXXXX', // Connected account ID
});
```

---

### Error: "Webhook signature verification failed"

**Cause:** The webhook secret in your code doesn't match the endpoint's signing secret.

**Solution:**
1. Go to `Developers → Webhooks` in Stripe Dashboard
2. Click on your endpoint
3. Reveal and copy the **Signing secret** (starts with `whsec_`)
4. Update your `STRIPE_WEBHOOK_SECRET` environment variable
5. If using Stripe CLI, use the secret shown when running `stripe listen`

---

### Error: "This account cannot make live charges"

**Cause:** Your Stripe account or the connected account isn't activated for live transactions.

**Solution:**
- For **test mode**: This is expected — live charges don't work in test mode
- For **live mode**: 
  - Ensure your platform account is activated (`Settings → Account settings → Activate`)
  - Connected accounts must complete onboarding and have active capabilities

---

### Error: "Application fee amount must be less than the charge amount"

**Cause:** Your application fee is greater than or equal to the transaction amount.

**Solution:**
```javascript
// Ensure fee is less than amount
const amount = 1000; // $10.00
const feePercent = 5; // 5%
const applicationFee = Math.round(amount * (feePercent / 100)); // $0.50

// Fee must be less than amount
if (applicationFee >= amount) {
  throw new Error('Fee cannot exceed transaction amount');
}
```

---

### Error: "The 'transfers' capability is not active on the connected account"

**Cause:** The connected account hasn't completed onboarding or hasn't been approved for transfers.

**Solution:**
1. Check account status:
   ```bash
   stripe accounts retrieve acct_1XXXXXXXXXXXXXXX
   ```
2. Look for `capabilities.transfers` — it should be `"active"`
3. If `"inactive"` or `"pending"`, the account needs to complete onboarding
4. Generate a new onboarding link (see section 4.6)

---

### Error: "Cannot create charges on connected accounts in test mode without onboarding"

**Cause:** Test accounts created via API may need explicit capability requests.

**Solution:**
```bash
# Create account with capabilities
stripe accounts create \
  --type=express \
  -d "capabilities[transfers][requested]=true" \
  -d "capabilities[card_payments][requested]=true" \
  -d "business_type=individual" \
  -d "business_profile[url]=https://example.com" \
  -d "business_profile[mcc]=5812"  # Restaurant/food service MCC
```

---

## 6. Test Checklist

Use this checklist to verify your Stripe Connect integration is working correctly.

### 6.1 Pre-Integration Checks

- [ ] Stripe account created and email verified
- [ ] Connect enabled in Stripe Dashboard (test mode)
- [ ] Express accounts enabled
- [ ] Branding configured (logo, colors, URLs)
- [ ] Webhook endpoint created
- [ ] Environment variables set:
  - [ ] `STRIPE_API_KEY`
  - [ ] `STRIPE_WEBHOOK_SECRET`
  - [ ] `STRIPE_PLATFORM_ACCOUNT_ID`
  - [ ] `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY`

### 6.2 Connected Account Onboarding

- [ ] Can create Express connected account via API
- [ ] Can generate onboarding link
- [ ] Onboarding flow loads correctly with your branding
- [ ] Return URL works after completion
- [ ] Refresh URL works if user reloads
- [ ] Account shows as `charges_enabled: true` after onboarding
- [ ] Account capabilities are `active` (transfers, card_payments)

### 6.3 Payment Flow

- [ ] Can create payment intent with `transfer_data[destination]`
- [ ] Can set `application_fee_amount` on payment intent
- [ ] Payment intent succeeds with test card `4242424242424242`
- [ ] Webhook `payment_intent.succeeded` fires and is received
- [ ] Webhook payload is correctly verified
- [ ] Platform fee is correctly calculated and recorded
- [ ] Connected account receives net amount (payment - fee - Stripe fees)

### 6.4 Webhook Handling

- [ ] `payment_intent.succeeded` updates order status
- [ ] `payment_intent.payment_failed` handles failed payments
- [ ] `account.updated` syncs account status changes
- [ ] `payout.paid` records payouts to connected accounts
- [ ] `charge.dispute.created` triggers dispute notifications

### 6.5 Payout Flow

- [ ] Connected account has bank account attached
- [ ] Payout schedule is configured (automatic/manual)
- [ ] `payout.created` webhook fires
- [ ] `payout.paid` webhook fires when funds arrive
- [ ] Payout amount matches expected balance

### 6.6 Error Handling

- [ ] Invalid webhook signatures are rejected (403)
- [ ] Failed payments show appropriate error messages
- [ ] Incomplete onboarding prevents payments
- [ ] Disconnected accounts are handled gracefully
- [ ] Network errors retry appropriately

### 6.7 Edge Cases

- [ ] Test 3D Secure cards (e.g., `4000002500003155`)
- [ ] Test insufficient funds (`4000000000009995`)
- [ ] Test expired card (`4000000000000069`)
- [ ] Test dispute flow (`4000000000000259`)
- [ ] Test refund flow

### 6.8 Production Readiness

- [ ] All test mode keys replaced with live keys
- [ ] Webhook endpoints updated to production URLs
- [ ] Platform account activated for live transactions
- [ ] Terms of service and privacy policy URLs are live
- [ ] Application fee percentage is configured correctly
- [ ] Monitoring/alerting set up for failed webhooks
- [ ] Webhook event logging is implemented

---

## Quick Reference

### Test Card Numbers

| Card Number | Scenario |
|-------------|----------|
| `4242424242424242` | Successful payment |
| `4000000000003220` | 3D Secure 2 (frictionless) |
| `4000002500003155` | 3D Secure 2 (challenge) |
| `4000000000009995` | Declined (insufficient funds) |
| `4000000000000069` | Declined (expired card) |
| `4000000000000127` | Declined (incorrect CVC) |
| `4000000000000259` | Dispute (create dispute webhook) |

### Important URLs

| Resource | URL |
|----------|-----|
| Stripe Dashboard | https://dashboard.stripe.com |
| Connect Settings | https://dashboard.stripe.com/settings/connect |
| Webhooks | https://dashboard.stripe.com/webhooks |
| API Keys | https://dashboard.stripe.com/apikeys |
| Connect Docs | https://stripe.com/docs/connect |

---

## Next Steps

1. **Implement the backend handlers** for account creation and webhook processing
2. **Build the frontend onboarding flow** using Stripe's Express onboarding links
3. **Set up monitoring** for webhook failures and payment issues
4. **Test with real users** in test mode before going live
5. **Activate live mode** when ready (requires additional verification)

For questions, refer to:
- [Stripe Connect Documentation](https://stripe.com/docs/connect)
- [Express Accounts Guide](https://stripe.com/docs/connect/express-accounts)
- [Webhook Best Practices](https://stripe.com/docs/webhooks/best-practices)
