# Use Cases: Subscription System

## Oversikt

Subscription-systemet styrer hva brukere kan gjøre basert på betalingsnivå. Dette er fundamentet for forretningsmodellen og gater features som antall køer, team-medlemmer, og avanserte funksjoner.

## Subscription Tiers

| Tier | Pris | Køer | Team | Features |
|------|------|------|------|----------|
| **Free** | 0 kr | 1 | Kun owner | Basic queue, 100 tickets/dag |
| **Starter** | 99 kr/mnd | 1 | 2 operators | 500 tickets/dag, e-post notif |
| **Pro** | 299 kr/mnd | 3 | 10 operators | Ubegrenset tickets, analytics, API |
| **Enterprise** | Kontakt | ∞ | ∞ | White-label, SLA, custom integrasjoner |

---

## UC-SUB-01: Se Min Subscription

**Aktør:** Authenticated user  
**Forutsetning:** Bruker er innlogget

### Hovedflyt
1. Bruker går til "Settings" → "Subscription"
2. System henter brukerens aktive subscription
3. System viser:
   - Nåværende plan
   - Forbruk (f.eks. "2/3 køer brukt")
   - Neste fakturadato
   - Betalingshistorikk
4. Knapper for upgrade/downgrade

### Resultat
Bruker ser sin subscription-status

---

## UC-SUB-02: Oppgrader Subscription

**Aktør:** User med Free eller lavere tier  
**Forutsetning:** Bruker ønsker flere features

### Hovedflyt
1. Bruker klikker "Upgrade" på subscription-siden eller fra en feature-gate
2. System viser tilgjengelige planer med prissammenlikning
3. Bruker velger ønsket plan
4. Bruker fyller inn betalingsinformasjon (Stripe checkout)
5. Betaling prosesseres
6. Ved suksess:
   - `Subscription`-record opprettes/oppdateres
   - Features låses umiddelbart opp
   - Bekreftelsesmail sendes
7. Bruker redirectes til dashboard med suksessmelding

### Alternative flyter
- **5a.** Betaling feiler: Vis feilmelding, behold nåværende plan
- **3a.** Bruker velger årlig fakturering: Vis rabattert pris (2 mnd gratis)

### Resultat
Bruker har høyere tier, flere features tilgjengelig

---

## UC-SUB-03: Downgrader Subscription

**Aktør:** User med betalt tier  
**Forutsetning:** Bruker ønsker å spare penger

### Hovedflyt
1. Bruker går til subscription-settings
2. Bruker klikker "Downgrade" eller "Cancel subscription"
3. System viser hva som vil mistes:
   - "Du vil miste tilgang til 2 av 3 køer"
   - "5 team-medlemmer vil bli fjernet"
4. System ber bruker velge hvilke køer/medlemmer som beholdes (om nødvendig)
5. Bruker bekrefter
6. Downgrade schedules til slutten av nåværende faktureringsperiode
7. Bekreftelsesmail sendes

### Resultat
Subscription nedgraderes ved periodeslutt

---

## UC-SUB-04: Feature Gate Check

**Aktør:** System (on behalf of user)  
**Forutsetning:** Bruker prøver å gjøre noe

### Hovedflyt
1. Use case (f.eks. `CreateQueueUseCase`) sjekker feature
2. System henter brukerens subscription
3. System sjekker om feature er tillatt:
   ```kotlin
   subscriptionService.canCreateQueue(userId) → true/false
   subscriptionService.canInviteMore(userId) → true/false
   subscriptionService.getMaxQueues(userId) → Int
   ```
4. Hvis tillatt: Fortsett med operasjon
5. Hvis ikke tillatt: Kast `FeatureNotAllowedException` med upgrade-info

### Alternative flyter
- **5a.** Controller fanger exception, viser upgrade-prompt til bruker

### Resultat
Bruker får enten gjennomført handling eller beskjed om å oppgradere

---

## UC-SUB-05: Webhook: Betaling Mottatt

**Aktør:** Stripe webhook  
**Forutsetning:** Kunde har betalt

### Hovedflyt
1. Stripe sender `invoice.payment_succeeded` webhook
2. System verifiserer webhook-signatur
3. System finner bruker basert på Stripe customer ID
4. System oppdaterer:
   - `subscription.status = ACTIVE`
   - `subscription.currentPeriodEnd = [fra Stripe]`
5. System logger betalingsevent

### Resultat
Subscription fornyet, bruker beholder tilgang

---

## UC-SUB-06: Webhook: Betaling Feilet

**Aktør:** Stripe webhook  
**Forutsetning:** Betaling kunne ikke gjennomføres

### Hovedflyt
1. Stripe sender `invoice.payment_failed` webhook
2. System markerer subscription som `PAST_DUE`
3. System sender varsel-epost til bruker
4. Etter 3 mislykkede forsøk (14 dager): Status → `CANCELLED`
5. Ved kansellering:
   - Features begrenses til Free tier
   - Køer utover limit deaktiveres (ikke slettet)
   - Team-medlemmer utover limit fjernes

### Resultat
Bruker varslet, eventuelt nedgradert

---

## UC-SUB-07: Prøveperiode (Trial)

**Aktør:** Ny bruker  
**Forutsetning:** Bruker registrerer seg for første gang

### Hovedflyt
1. Bruker registrerer seg
2. System oppretter automatisk 14-dagers Pro trial
3. Bruker har full tilgang til Pro-features
4. System sender påminnelser:
   - Dag 7: "7 dager igjen av trial"
   - Dag 12: "2 dager igjen - legg til betalingsmetode"
   - Dag 14: "Trial utløpt"
5. Ved trial-slutt: Downgrade til Free (eller betalt hvis kort lagt til)

### Resultat
Bruker får smake på Pro før de må betale

---

## Datamodell

### Subscription

```kotlin
data class Subscription(
    val id: UUID,
    val userId: String,  // Keycloak subject
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val stripeCustomerId: String?,
    val stripeSubscriptionId: String?,
    val currentPeriodStart: Instant,
    val currentPeriodEnd: Instant,
    val cancelAtPeriodEnd: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class SubscriptionTier {
    FREE,
    STARTER,
    PRO,
    ENTERPRISE
}

enum class SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    INCOMPLETE
}
```

### SubscriptionLimits (Config/Companion Object)

```kotlin
object SubscriptionLimits {
    fun forTier(tier: SubscriptionTier) = when(tier) {
        FREE -> Limits(maxQueues = 1, maxOperators = 0, maxTicketsPerDay = 100, hasEmail = false, hasAnalytics = false)
        STARTER -> Limits(maxQueues = 1, maxOperators = 2, maxTicketsPerDay = 500, hasEmail = true, hasAnalytics = false)
        PRO -> Limits(maxQueues = 3, maxOperators = 10, maxTicketsPerDay = Int.MAX_VALUE, hasEmail = true, hasAnalytics = true)
        ENTERPRISE -> Limits(maxQueues = Int.MAX_VALUE, maxOperators = Int.MAX_VALUE, maxTicketsPerDay = Int.MAX_VALUE, hasEmail = true, hasAnalytics = true, hasApi = true, hasWhiteLabel = true)
    }
}

data class Limits(
    val maxQueues: Int,
    val maxOperators: Int,
    val maxTicketsPerDay: Int,
    val hasEmail: Boolean,
    val hasAnalytics: Boolean,
    val hasApi: Boolean = false,
    val hasWhiteLabel: Boolean = false
)
```

---

## API Endpoints

| Method | Endpoint | Beskrivelse | Auth |
|--------|----------|-------------|------|
| GET | `/api/subscription` | Get my subscription | Authenticated |
| GET | `/api/subscription/limits` | Get my current limits | Authenticated |
| POST | `/api/subscription/checkout` | Create Stripe checkout session | Authenticated |
| POST | `/api/subscription/portal` | Create Stripe billing portal link | Authenticated |
| POST | `/api/webhooks/stripe` | Stripe webhook handler | Stripe signature |

---

## SubscriptionService Interface

```kotlin
interface SubscriptionService {
    fun getSubscription(userId: String): Subscription
    fun getTier(userId: String): SubscriptionTier
    fun getLimits(userId: String): Limits
    
    // Feature checks
    fun canCreateQueue(userId: String): Boolean
    fun canInviteOperator(userId: String, queueId: UUID): Boolean
    fun canIssueTicket(queueId: UUID): Boolean  // Check daily limit
    fun hasFeature(userId: String, feature: Feature): Boolean
    
    // Billing
    fun createCheckoutSession(userId: String, tier: SubscriptionTier): String  // Returns Stripe URL
    fun createBillingPortalSession(userId: String): String
    
    // Webhook handlers
    fun handlePaymentSucceeded(event: StripeEvent)
    fun handlePaymentFailed(event: StripeEvent)
    fun handleSubscriptionUpdated(event: StripeEvent)
}
```

---

## Feature Gating i Eksisterende Use Cases

### CreateQueueUseCase (oppdatert)

```kotlin
class CreateQueueUseCase(
    private val queueRepository: QueueRepository,
    private val queueStateRepository: QueueStateRepository,
    private val subscriptionService: SubscriptionService  // NEW
) {
    fun execute(name: String, ownerId: String): Queue {
        // CHANGED: Replace hardcoded limit with subscription check
        if (!subscriptionService.canCreateQueue(ownerId)) {
            val limits = subscriptionService.getLimits(ownerId)
            throw FeatureNotAllowedException(
                "Queue limit reached (${limits.maxQueues}). Upgrade to create more queues.",
                upgradeRequired = true
            )
        }
        
        val queue = Queue.create(name, ownerId)
        queueRepository.save(queue)
        // ... create default states
        return queue
    }
}
```

### IssueTicketUseCase (oppdatert)

```kotlin
class IssueTicketUseCase(
    // ... existing deps
    private val subscriptionService: SubscriptionService  // NEW
) {
    fun execute(queueId: UUID, qrSecret: String, name: String?, email: String?): Ticket {
        // NEW: Check daily ticket limit
        if (!subscriptionService.canIssueTicket(queueId)) {
            throw FeatureNotAllowedException(
                "Daily ticket limit reached. Queue owner needs to upgrade.",
                upgradeRequired = true
            )
        }
        
        // ... rest of existing logic
    }
}
```

---

## Sikkerhetshensyn

1. **Stripe webhook verification**: Alltid verifiser signatur
2. **Server-side validation**: Aldri stol på client for limit-sjekk
3. **Graceful degradation**: Ved downgrade, deaktiver (ikke slett) data
4. **Audit logging**: Logg alle subscription-endringer
5. **PCI compliance**: Aldri lagre kortdata, bruk Stripe Elements

---

## UI-skisser

### Subscription Page
```
┌────────────────────────────────────────┐
│ Your Subscription                      │
├────────────────────────────────────────┤
│ Current Plan: STARTER                  │
│ Status: Active ✓                       │
│ Renews: Feb 15, 2025                   │
├────────────────────────────────────────┤
│ Usage This Period:                     │
│ ├─ Queues: 1/1 ███████████████ 100%   │
│ ├─ Team:   2/2 ███████████████ 100%   │
│ └─ Tickets: 234/500 ██████████░ 47%   │
├────────────────────────────────────────┤
│ [Upgrade to Pro]  [Manage Billing]     │
└────────────────────────────────────────┘
```

### Upgrade Prompt (Feature Gate)
```
┌────────────────────────────────────────┐
│        ⚡ Upgrade Required             │
├────────────────────────────────────────┤
│                                        │
│  You've reached the queue limit for    │
│  your current plan.                    │
│                                        │
│  Upgrade to PRO to create up to        │
│  3 queues and unlock analytics.        │
│                                        │
│  PRO: 299 kr/month                     │
│                                        │
│  [Upgrade Now]  [Maybe Later]          │
│                                        │
└────────────────────────────────────────┘
```

### Pricing Page
```
┌──────────────────────────────────────────────────────────┐
│                    Choose Your Plan                       │
├──────────────┬──────────────┬──────────────┬─────────────┤
│    FREE      │   STARTER    │     PRO      │ ENTERPRISE  │
│    0 kr      │  99 kr/mo    │  299 kr/mo   │  Contact us │
├──────────────┼──────────────┼──────────────┼─────────────┤
│ 1 Queue      │ 1 Queue      │ 3 Queues     │ Unlimited   │
│ Owner only   │ 2 Operators  │ 10 Operators │ Unlimited   │
│ 100 tix/day  │ 500 tix/day  │ Unlimited    │ Unlimited   │
│              │ Email notif  │ Analytics    │ White-label │
│              │              │ API access   │ Custom SLA  │
├──────────────┼──────────────┼──────────────┼─────────────┤
│ [Current]    │ [Upgrade]    │ [Upgrade]    │ [Contact]   │
└──────────────┴──────────────┴──────────────┴─────────────┘
```
