# Implementation Plan: Invite System & Subscriptions

## Executive Summary

Denne planen beskriver implementasjon av to tett relaterte features: **Invite System** og **Subscription Tiers**. De må implementeres sammen fordi invites er gated av subscription tier.

**Total estimat:** 6-8 uker (1 utvikler)  
**Prioritet:** Subscription først (foundation), deretter Invites

---

## Fase 1: Database & Domain Foundation (Uke 1)

### Oppgaver

#### 1.1 Database Migrations

**Fil:** `V5__add_subscriptions.sql`

```sql
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    tier VARCHAR(50) NOT NULL DEFAULT 'FREE',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    current_period_start TIMESTAMP NOT NULL,
    current_period_end TIMESTAMP NOT NULL,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_stripe_customer ON subscriptions(stripe_customer_id);
```

**Fil:** `V6__add_invites_and_members.sql`

```sql
CREATE TABLE queue_members (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    invited_by VARCHAR(255),
    UNIQUE(queue_id, user_id)
);

CREATE TABLE invites (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    accepted_by_user_id VARCHAR(255)
);

CREATE INDEX idx_invites_token ON invites(token);
CREATE INDEX idx_invites_queue_status ON invites(queue_id, status);
CREATE INDEX idx_queue_members_user ON queue_members(user_id);
```

**Estimat:** 2 timer  
**Risiko:** Lav  
**Dependencies:** Ingen

---

#### 1.2 Domain Models

**Nye filer i `domain/model/`:**

- `Subscription.kt`
- `QueueMember.kt`
- `Invite.kt`
- `SubscriptionTier.kt` (enum)
- `MemberRole.kt` (enum)

**Estimat:** 3 timer  
**Risiko:** Lav  
**Dependencies:** Ingen

---

#### 1.3 Repository Interfaces

**Oppdater `domain/port/Repositories.kt`:**

```kotlin
interface SubscriptionRepository {
    fun save(subscription: Subscription)
    fun findByUserId(userId: String): Subscription?
    fun findByStripeCustomerId(customerId: String): Subscription?
}

interface QueueMemberRepository {
    fun save(member: QueueMember)
    fun findByQueueId(queueId: UUID): List<QueueMember>
    fun findByUserId(userId: String): List<QueueMember>
    fun findByQueueIdAndUserId(queueId: UUID, userId: String): QueueMember?
    fun delete(id: UUID)
    fun countByQueueId(queueId: UUID): Int
}

interface InviteRepository {
    fun save(invite: Invite)
    fun findById(id: UUID): Invite?
    fun findByToken(token: String): Invite?
    fun findByQueueId(queueId: UUID): List<Invite>
    fun findPendingByQueueId(queueId: UUID): List<Invite>
    fun countPendingByQueueId(queueId: UUID): Int
    fun delete(id: UUID)
}
```

**Estimat:** 2 timer  
**Risiko:** Lav  
**Dependencies:** 1.2

---

### Deliverables Fase 1
- [x] Database schema for subscriptions, members, invites
- [x] Domain models
- [x] Repository interfaces

**Total Fase 1:** ~1 dag

---

## Fase 2: Subscription Service (Uke 1-2)

### Oppgaver

#### 2.1 SubscriptionService Implementation

**Ny fil:** `application/service/SubscriptionService.kt`

Core subscription logic uten Stripe-integrasjon først:

```kotlin
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val queueRepository: QueueRepository,
    private val queueMemberRepository: QueueMemberRepository
) {
    fun getOrCreateSubscription(userId: String): Subscription
    fun getTier(userId: String): SubscriptionTier
    fun getLimits(userId: String): SubscriptionLimits
    
    fun canCreateQueue(userId: String): Boolean {
        val limits = getLimits(userId)
        val currentQueues = queueRepository.findByOwnerId(userId).size
        return currentQueues < limits.maxQueues
    }
    
    fun canInviteOperator(userId: String, queueId: UUID): Boolean {
        val limits = getLimits(userId)
        val currentMembers = queueMemberRepository.countByQueueId(queueId)
        return currentMembers < limits.maxOperators
    }
}
```

**Estimat:** 4 timer  
**Risiko:** Lav  
**Dependencies:** 1.3

---

#### 2.2 JDBC Repository Implementations

**Nye filer i `infrastructure/adapter/persistence/`:**

- `JdbcSubscriptionRepository.kt`
- `JdbcQueueMemberRepository.kt`
- `JdbcInviteRepository.kt`

**Estimat:** 4 timer  
**Risiko:** Lav  
**Dependencies:** 1.3, 2.1

---

#### 2.3 Update CreateQueueUseCase

Fjern hardkodet "You can only have one queue" og bruk SubscriptionService:

```kotlin
class CreateQueueUseCase(
    private val queueRepository: QueueRepository,
    private val queueStateRepository: QueueStateRepository,
    private val subscriptionService: SubscriptionService  // ADD
) {
    fun execute(name: String, ownerId: String): Queue {
        // REPLACE hardcoded check with subscription check
        if (!subscriptionService.canCreateQueue(ownerId)) {
            throw FeatureNotAllowedException("Upgrade to create more queues")
        }
        // ... rest unchanged
    }
}
```

**Estimat:** 1 time  
**Risiko:** Lav  
**Dependencies:** 2.1

---

#### 2.4 Subscription API Endpoints

**Ny fil:** `infrastructure/controller/SubscriptionController.kt`

```kotlin
@RestController
@RequestMapping("/api/subscription")
class SubscriptionController(
    private val subscriptionService: SubscriptionService
) {
    @GetMapping
    fun getMySubscription(auth: Authentication): SubscriptionDTO
    
    @GetMapping("/limits")
    fun getMyLimits(auth: Authentication): LimitsDTO
}
```

**Estimat:** 2 timer  
**Risiko:** Lav  
**Dependencies:** 2.1, 2.2

---

### Deliverables Fase 2
- [x] SubscriptionService med limit-checking
- [x] Repository implementations
- [x] CreateQueueUseCase bruker subscription limits
- [x] Basic subscription API

**Total Fase 2:** ~2 dager

---

## Fase 3: Invite System Core (Uke 2-3)

### Oppgaver

#### 3.1 Invite Use Cases

**Nye filer i `application/usecase/`:**

```kotlin
// SendInviteUseCase.kt
class SendInviteUseCase(
    private val inviteRepository: InviteRepository,
    private val queueRepository: QueueRepository,
    private val subscriptionService: SubscriptionService,
    private val emailPort: EmailPort
) {
    fun execute(queueId: UUID, email: String, role: MemberRole, inviterId: String): Invite
}

// AcceptInviteUseCase.kt
class AcceptInviteUseCase(
    private val inviteRepository: InviteRepository,
    private val queueMemberRepository: QueueMemberRepository
) {
    fun execute(token: String, userId: String): QueueMember
}

// RevokeInviteUseCase.kt
class RevokeInviteUseCase(
    private val inviteRepository: InviteRepository
) {
    fun execute(inviteId: UUID, ownerId: String)
}

// RemoveMemberUseCase.kt
class RemoveMemberUseCase(
    private val queueMemberRepository: QueueMemberRepository,
    private val queueRepository: QueueRepository
) {
    fun execute(queueId: UUID, memberId: UUID, ownerId: String)
}

// GetMyQueuesUseCase.kt
class GetMyQueuesUseCase(
    private val queueRepository: QueueRepository,
    private val queueMemberRepository: QueueMemberRepository
) {
    fun execute(userId: String): List<QueueWithRole>
}
```

**Estimat:** 6 timer  
**Risiko:** Middels (email sending, token handling)  
**Dependencies:** 2.1, 2.2

---

#### 3.2 Email Templates

**Ny fil:** `infrastructure/adapter/email/InviteEmailTemplate.kt`

Bruk Spring's `JavaMailSender` eller eksisterende `EmailPort`:

```kotlin
class InviteEmailAdapter(
    private val mailSender: JavaMailSender,
    private val baseUrl: String
) : EmailPort {
    fun sendInviteEmail(invite: Invite, queue: Queue, inviterName: String) {
        // HTML email med invite link: $baseUrl/invite/${invite.token}
    }
}
```

**Estimat:** 3 timer  
**Risiko:** Middels (SMTP config, template design)  
**Dependencies:** 3.1

---

#### 3.3 Invite API Endpoints

**Oppdater:** `infrastructure/controller/OwnerQueueController.kt`

Legg til:
- `POST /api/owner/queues/{id}/invites`
- `GET /api/owner/queues/{id}/invites`
- `DELETE /api/owner/queues/{id}/invites/{inviteId}`
- `GET /api/owner/queues/{id}/members`
- `DELETE /api/owner/queues/{id}/members/{memberId}`

**Ny fil:** `infrastructure/controller/InviteController.kt`

```kotlin
@RestController
@RequestMapping("/api/invites")
class InviteController(
    private val acceptInviteUseCase: AcceptInviteUseCase
) {
    @GetMapping("/{token}")
    fun getInviteInfo(@PathVariable token: String): InviteInfoDTO  // Public
    
    @PostMapping("/{token}/accept")
    fun acceptInvite(@PathVariable token: String, auth: Authentication): QueueMemberDTO
    
    @PostMapping("/{token}/decline")
    fun declineInvite(@PathVariable token: String)  // Public
}
```

**Estimat:** 4 timer  
**Risiko:** Lav  
**Dependencies:** 3.1

---

#### 3.4 Authorization: Operator Access

**Kritisk endring:** Operators må kunne serve tickets.

**Oppdater:** `OwnerQueueController.kt`

```kotlin
// BEFORE: checkOwnership() checks queue.ownerId == userId

// AFTER: checkAccess() checks ownership OR membership
private fun checkAccess(queueId: UUID, authentication: Authentication, requiredRole: MemberRole = MemberRole.OPERATOR) {
    val userId = getOwnerId(authentication.principal)
    val queue = queueRepository.findById(queueId) ?: throw NotFoundException()
    
    if (queue.ownerId == userId) return  // Owner has all access
    
    val membership = queueMemberRepository.findByQueueIdAndUserId(queueId, userId)
    if (membership == null || membership.role < requiredRole) {
        throw AccessDeniedException("Insufficient permissions")
    }
}
```

**Estimat:** 3 timer  
**Risiko:** Høy (security-critical)  
**Dependencies:** 3.1, 3.3

---

### Deliverables Fase 3
- [x] Send, accept, revoke invite use cases
- [x] Email sending for invites
- [x] API endpoints for invite management
- [x] Operators can access queue operations

**Total Fase 3:** ~3 dager

---

## Fase 4: Stripe Integration (Uke 3-4)

### Oppgaver

#### 4.1 Stripe Dependencies

**Oppdater:** `build.gradle`

```groovy
dependencies {
    implementation 'com.stripe:stripe-java:24.0.0'
}
```

**Estimat:** 30 min  
**Dependencies:** Ingen

---

#### 4.2 Stripe Configuration

**Ny fil:** `infrastructure/config/StripeConfig.kt`

```kotlin
@Configuration
class StripeConfig {
    @Value("\${stripe.api-key}")
    private lateinit var apiKey: String
    
    @Value("\${stripe.webhook-secret}")
    private lateinit var webhookSecret: String
    
    @PostConstruct
    fun init() {
        Stripe.apiKey = apiKey
    }
}
```

**Oppdater:** `application.yml`

```yaml
stripe:
  api-key: ${STRIPE_API_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  prices:
    starter: price_xxx
    pro: price_yyy
```

**Estimat:** 1 time  
**Risiko:** Middels (secrets management)  
**Dependencies:** 4.1

---

#### 4.3 Stripe Service

**Ny fil:** `infrastructure/adapter/payment/StripePaymentAdapter.kt`

```kotlin
class StripePaymentAdapter(
    private val subscriptionRepository: SubscriptionRepository,
    private val priceConfig: Map<SubscriptionTier, String>
) {
    fun createCheckoutSession(userId: String, tier: SubscriptionTier, successUrl: String, cancelUrl: String): String {
        // Create or get Stripe customer
        // Create checkout session
        // Return session URL
    }
    
    fun createBillingPortalSession(userId: String): String {
        // Return portal URL for managing subscription
    }
    
    fun handleWebhook(payload: String, signature: String) {
        // Verify signature
        // Route to appropriate handler
    }
}
```

**Estimat:** 6 timer  
**Risiko:** Høy (payment processing)  
**Dependencies:** 4.2

---

#### 4.4 Webhook Controller

**Ny fil:** `infrastructure/controller/StripeWebhookController.kt`

```kotlin
@RestController
@RequestMapping("/api/webhooks")
class StripeWebhookController(
    private val stripePaymentAdapter: StripePaymentAdapter
) {
    @PostMapping("/stripe")
    fun handleStripeWebhook(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String
    ): ResponseEntity<String> {
        stripePaymentAdapter.handleWebhook(payload, signature)
        return ResponseEntity.ok("OK")
    }
}
```

**Viktig:** Legg til i `SecurityConfig`:
```kotlin
.requestMatchers("/api/webhooks/stripe").permitAll()
```

**Estimat:** 3 timer  
**Risiko:** Middels  
**Dependencies:** 4.3

---

#### 4.5 Subscription Lifecycle Handlers

Events å håndtere:
- `checkout.session.completed` → Create/upgrade subscription
- `invoice.payment_succeeded` → Renew subscription
- `invoice.payment_failed` → Mark as past_due
- `customer.subscription.updated` → Sync tier changes
- `customer.subscription.deleted` → Downgrade to FREE

**Estimat:** 4 timer  
**Risiko:** Høy (business logic, edge cases)  
**Dependencies:** 4.3, 4.4

---

### Deliverables Fase 4
- [x] Stripe SDK integration
- [x] Checkout flow for upgrades
- [x] Billing portal for self-service
- [x] Webhook handling for all subscription events
- [x] Automatic tier sync

**Total Fase 4:** ~3 dager

---

## Fase 5: Frontend Integration (Uke 5-6)

### Oppgaver

#### 5.1 Subscription Page

- Vis nåværende plan og forbruk
- Upgrade-knapper
- Link til Stripe billing portal

**Estimat:** 6 timer  
**Dependencies:** 2.4, 4.3

---

#### 5.2 Team Management UI

- Liste over team-medlemmer
- Pending invites med resend/cancel
- Invite-form (email input)
- Remove member med confirmation

**Estimat:** 8 timer  
**Dependencies:** 3.3

---

#### 5.3 Invite Accept Page

- `/invite/{token}` route
- Vis kø-info og rolle
- Accept/Decline buttons
- Redirect til login hvis ikke autentisert

**Estimat:** 4 timer  
**Dependencies:** 3.3

---

#### 5.4 Feature Gate UI

- Upgrade prompts når limits nås
- Usage indicators i dashboard
- Pricing comparison modal

**Estimat:** 4 timer  
**Dependencies:** 5.1

---

#### 5.5 Operator Dashboard

- Operators ser køer de er medlem av
- Begrenset UI (ingen settings/delete)
- Serve/complete ticket funksjonalitet

**Estimat:** 6 timer  
**Dependencies:** 3.4

---

### Deliverables Fase 5
- [x] Subscription settings page
- [x] Team management in dashboard
- [x] Invite accept flow
- [x] Feature gate prompts
- [x] Operator-specific dashboard view

**Total Fase 5:** ~4 dager

---

## Fase 6: Testing & Polish (Uke 6-7)

### Oppgaver

#### 6.1 Unit Tests

- SubscriptionService limit checks
- Invite use cases
- Authorization logic

**Estimat:** 4 timer

---

#### 6.2 Integration Tests

- Full invite flow (send → accept → serve ticket)
- Subscription upgrade flow
- Webhook handling

**Estimat:** 6 timer

---

#### 6.3 E2E Tests

- Stripe test mode checkout
- Multi-user scenarios

**Estimat:** 4 timer

---

#### 6.4 Cleanup Job

Cron job for å:
- Expire old invites
- Downgrade expired trials
- Clean up cancelled subscriptions

**Estimat:** 2 timer

---

### Deliverables Fase 6
- [x] 80%+ test coverage for new code
- [x] E2E tests passing
- [x] Background jobs running

**Total Fase 6:** ~2 dager

---

## Dependency Graph

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Fase 1: Foundation                                             │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐                    │
│  │ 1.1 DB   │──▶│ 1.2 Model│──▶│ 1.3 Repos│                    │
│  │ Migration│   │          │   │ Interface│                    │
│  └──────────┘   └──────────┘   └────┬─────┘                    │
│                                      │                          │
├──────────────────────────────────────┼──────────────────────────┤
│                                      ▼                          │
│  Fase 2: Subscription Service   ┌──────────┐                   │
│                                 │ 2.1 Svc  │                   │
│  ┌──────────┐                   │ Impl     │                   │
│  │ 2.2 JDBC │◀──────────────────┤          │                   │
│  │ Repos    │                   └────┬─────┘                   │
│  └────┬─────┘                        │                          │
│       │      ┌──────────┐            │     ┌──────────┐        │
│       └─────▶│ 2.3 Upd  │◀───────────┘     │ 2.4 API  │        │
│              │ UseCase  │                   │ Endpoint │        │
│              └──────────┘                   └──────────┘        │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Fase 3: Invite System                                          │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐    │
│  │ 3.1 Use  │──▶│ 3.2 Email│   │ 3.3 API  │──▶│ 3.4 Auth │    │
│  │ Cases    │   │ Template │   │ Endpoints│   │ Operator │    │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘    │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Fase 4: Stripe          ┌──────────┐                          │
│  ┌──────────┐           │ 4.2 Cfg  │                          │
│  │ 4.1 Deps │──────────▶│          │──────────┐               │
│  └──────────┘           └──────────┘          │               │
│                              │                 ▼               │
│                              │           ┌──────────┐          │
│                              └──────────▶│ 4.3 Svc  │          │
│                                          │          │          │
│  ┌──────────┐   ┌──────────┐            └────┬─────┘          │
│  │ 4.4 Hook │◀──┤ 4.5 Life │◀────────────────┘               │
│  │ Ctrl     │   │ cycle    │                                   │
│  └──────────┘   └──────────┘                                   │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Fase 5: Frontend        Fase 6: Testing                       │
│  ┌──────────┐           ┌──────────┐                          │
│  │ 5.1-5.5  │           │ 6.1-6.4  │                          │
│  │ UI Work  │           │ Tests    │                          │
│  └──────────┘           └──────────┘                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Risikomatrise

| Risiko | Sannsynlighet | Impact | Mitigering |
|--------|---------------|--------|------------|
| Stripe webhook feil | Middels | Høy | Grundig logging, retry-mekanisme, alerting |
| Auth-bypass for operators | Lav | Kritisk | Ekstra code review, security tests |
| Email deliverability | Middels | Middels | Bruk SendGrid/SES, monitor bounce rates |
| Migration rollback | Lav | Høy | Test migrations i staging først |
| Performance (member lookups) | Lav | Middels | Indekser på queue_id, user_id |

---

## Milepæler

| Uke | Milestone | Demo-ready |
|-----|-----------|------------|
| 1 | Database + Domain models | ❌ |
| 2 | Subscription limits fungerer | ✅ Basic subscription |
| 3 | Invite flow komplett | ✅ Team invites |
| 4 | Stripe betalinger | ✅ Full subscription |
| 5-6 | Frontend komplett | ✅ Full release |
| 6-7 | Testing + polish | ✅ Production ready |

---

## Neste Steg

1. **Opprett feature branch:** `git checkout -b feature/subscriptions-and-invites`
2. **Start med Fase 1.1:** Lag database migrations
3. **Sett opp Stripe test-konto** og få API keys
4. **Definer pricing** i Stripe dashboard (Products + Prices)

---

## Spørsmål å avklare

1. **Trial periode?** 14 dager Pro trial for nye brukere?
2. **Årlig rabatt?** 2 måneder gratis ved årlig betaling?
3. **Grandfathering?** Eksisterende brukere får FREE eller STARTER?
4. **Email provider?** SMTP, SendGrid, AWS SES?
5. **Operator self-signup?** Kan operators registrere seg direkte, eller kun via invite?
