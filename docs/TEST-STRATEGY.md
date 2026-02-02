# Test Strategy - Simple Queue

## Test Users

All test users use password: `Test123!`

### Sellers (Commission-based sales)
| Username | Email | Name | Role |
|----------|-------|------|------|
| seller-anna | seller-anna@test.queue.knobo.no | Anna Selger | `seller` |
| seller-bob | seller-bob@test.queue.knobo.no | Bob Selger | `seller` |

### Queue Owners (Subscription-based)
| Username | Email | Name | Role |
|----------|-------|------|------|
| owner-cafe | owner-cafe@test.queue.knobo.no | Cafe Eier | `queue-owner` |
| owner-salon | owner-salon@test.queue.knobo.no | Salon Eier | `queue-owner` |
| owner-clinic | owner-clinic@test.queue.knobo.no | Clinic Eier | `queue-owner` |

### Customers (End users)
| Username | Email | Name | Role |
|----------|-------|------|------|
| customer-ole | customer-ole@test.queue.knobo.no | Ole Kunde | *(none)* |
| customer-kari | customer-kari@test.queue.knobo.no | Kari Kunde | *(none)* |
| customer-per | customer-per@test.queue.knobo.no | Per Kunde | *(none)* |

### Admin
| Username | Email | Role |
|----------|-------|------|
| bohmer@gmail.com | bohmer@gmail.com | `superadmin` |

---

## Test Cases

### 1. Authentication & Authorization

#### 1.1 Login/Logout
- [ ] User can log in with email/password
- [ ] User can log out
- [ ] Invalid credentials show error message
- [ ] Session persists across page refreshes

#### 1.2 Role-based Access
- [ ] Superadmin can access `/admin/tier-limits`
- [ ] Regular users cannot access admin endpoints (403)
- [ ] Queue owners can only see their own queues
- [ ] Sellers can only see queues they're assigned to

---

### 2. Subscription & Billing

#### 2.1 Subscription Tiers
| Tier | Price | Max Queues | Max Operators | Max Tickets/Day |
|------|-------|------------|---------------|-----------------|
| FREE | 0 kr | 1 | 0 | 50 |
| STARTER | 99 kr/mo | 3 | 2 | 200 |
| PROFESSIONAL | 299 kr/mo | 10 | 10 | Unlimited |
| ENTERPRISE | 999 kr/mo | Unlimited | Unlimited | Unlimited |

#### 2.2 Upgrade Flow
- [ ] FREE user sees upgrade options on `/subscription`
- [ ] Clicking "Upgrade" redirects to Stripe Checkout
- [ ] After payment, user is redirected to `/subscription/success`
- [ ] Subscription tier updates to new level
- [ ] Webhook processes `checkout.session.completed`

#### 2.3 Downgrade Flow
- [ ] User can cancel subscription
- [ ] Subscription remains active until period ends
- [ ] After period ends, user reverts to FREE tier

#### 2.4 Stripe Webhooks
- [ ] `checkout.session.completed` → creates/updates subscription
- [ ] `customer.subscription.updated` → updates tier/status
- [ ] `customer.subscription.deleted` → reverts to FREE
- [ ] `invoice.payment_failed` → marks subscription as past_due

---

### 3. Tier Limits Enforcement

#### 3.1 Queue Limits
| Test Case | User Tier | Action | Expected |
|-----------|-----------|--------|----------|
| Create queue within limit | FREE (1 queue) | Create 1st queue | Success |
| Create queue exceeds limit | FREE (1 queue) | Create 2nd queue | Error: "Upgrade to create more queues" |
| Create queue after upgrade | STARTER | Create 2nd queue | Success |

#### 3.2 Operator Limits
| Test Case | User Tier | Action | Expected |
|-----------|-----------|--------|----------|
| Add operator (FREE) | FREE | Add operator | Error: "Upgrade to add operators" |
| Add operator within limit | STARTER | Add 1st operator | Success |
| Add operator exceeds limit | STARTER | Add 3rd operator | Error: "Upgrade to add more operators" |

#### 3.3 Ticket Limits
| Test Case | User Tier | Action | Expected |
|-----------|-----------|--------|----------|
| Create ticket within limit | FREE | Create ticket (under 50/day) | Success |
| Create ticket exceeds limit | FREE | Create 51st ticket | Error: "Daily ticket limit reached" |

---

### 4. Queue Management

#### 4.1 Queue CRUD
- [ ] Queue owner can create a queue
- [ ] Queue owner can edit queue details
- [ ] Queue owner can delete a queue
- [ ] Queue owner can view queue statistics

#### 4.2 Queue Operations
- [ ] Generate QR code for queue
- [ ] Customers can join queue via QR code
- [ ] Queue displays current position
- [ ] Queue owner can call next customer
- [ ] Queue owner can skip customer
- [ ] Queue owner can remove customer

---

### 5. Ticket Flow

#### 5.1 Customer Journey
1. [ ] Customer scans QR code
2. [ ] Customer sees queue info and joins
3. [ ] Customer receives ticket number
4. [ ] Customer sees position in queue
5. [ ] Customer receives notification when called
6. [ ] Customer marks as served or leaves

#### 5.2 Operator Journey
1. [ ] Operator logs in
2. [ ] Operator sees assigned queues
3. [ ] Operator calls next customer
4. [ ] Operator marks customer as served
5. [ ] Operator can add notes to ticket

---

### 6. Superadmin Functions

#### 6.1 Tier Limits Management
- [ ] View all tier limits at `/admin/tier-limits`
- [ ] Edit limits for each tier
- [ ] Changes persist after save
- [ ] Cache invalidates after update

#### 6.2 User Management
- [ ] View all users
- [ ] Change user subscription tier manually
- [ ] View user's subscription history

---

### 7. Integration Tests

#### 7.1 Stripe Integration
- [ ] Test mode payments work with test cards
- [ ] Webhook signature validation works
- [ ] Idempotent webhook processing (no duplicates)

#### 7.2 Keycloak Integration
- [ ] OAuth2 login flow works
- [ ] Token refresh works
- [ ] Role claims are correctly parsed

---

## Test Environments

| Environment | URL | Stripe Mode |
|-------------|-----|-------------|
| Development | localhost:8080 | Test |
| Staging | queue.knobo.no | Test |
| Production | TBD | Live |

## Test Cards (Stripe Test Mode)

| Card Number | Scenario |
|-------------|----------|
| 4242 4242 4242 4242 | Successful payment |
| 4000 0000 0000 0002 | Card declined |
| 4000 0000 0000 3220 | 3D Secure required |

---

## Running Tests

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew integrationTest

# E2E tests (requires running server)
./gradlew e2eTest
```

---

## Coverage Goals

- Unit tests: 80%+ coverage on domain/application layers
- Integration tests: All API endpoints
- E2E tests: Critical user journeys (signup, upgrade, queue flow)
