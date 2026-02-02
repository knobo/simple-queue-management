# Plan Limits Enforcement - Implementasjonsplan

## Oversikt

Implementere konfigurerbare begrensninger per subscription tier som lagres i database og kan administreres av superadmin.

## Nåværende tilstand

Limits er hardkodet i `SubscriptionService.SubscriptionLimits.forTier()`:
- FREE: 1 kø, 0 operatører, 50 tickets/dag
- STARTER: 3 køer, 2 operatører, 200 tickets/dag  
- PRO: 10 køer, 10 operatører, ubegrenset tickets
- ENTERPRISE: ubegrenset alt

## Målarkitektur

```
┌─────────────────────────────────────────────────────────────┐
│                     Superadmin UI                           │
│              /admin/tier-limits (Thymeleaf)                 │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  TierLimitsController                       │
│            GET/PUT /api/admin/tier-limits                   │
│            @PreAuthorize("hasRole('SUPERADMIN')")           │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  SubscriptionService                        │
│         getLimits() → henter fra TierLimitRepository        │
│         canCreateQueue(), canInviteOperator(), etc.         │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  TierLimitRepository                        │
│         findByTier(), save(), findAll()                     │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│              tier_limits (PostgreSQL)                       │
│  tier | max_queues | max_operators | max_tickets_day | ...  │
└─────────────────────────────────────────────────────────────┘
```

## Implementasjonsplan

### Fase 1: Database & Domain

**1.1 Flyway-migrasjon (`V14__add_tier_limits.sql`)**
```sql
CREATE TABLE tier_limits (
    tier VARCHAR(50) PRIMARY KEY,
    max_queues INTEGER NOT NULL,
    max_operators_per_queue INTEGER NOT NULL,
    max_tickets_per_day INTEGER NOT NULL,
    max_active_tickets INTEGER NOT NULL,
    max_invites_per_month INTEGER NOT NULL,
    can_use_email_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    can_use_custom_branding BOOLEAN NOT NULL DEFAULT FALSE,
    can_use_analytics BOOLEAN NOT NULL DEFAULT FALSE,
    can_use_api_access BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(255)
);

-- Seed default values (matching current hardcoded limits)
INSERT INTO tier_limits VALUES
('FREE', 1, 0, 50, 100, 5, FALSE, FALSE, FALSE, FALSE, NOW(), 'system'),
('STARTER', 3, 2, 200, 500, 20, TRUE, FALSE, FALSE, FALSE, NOW(), 'system'),
('PRO', 10, 10, -1, -1, -1, TRUE, TRUE, TRUE, FALSE, NOW(), 'system'),
('ENTERPRISE', -1, -1, -1, -1, -1, TRUE, TRUE, TRUE, TRUE, NOW(), 'system');
-- -1 = ubegrenset
```

**1.2 Domain model (`TierLimit.kt`)**
```kotlin
data class TierLimit(
    val tier: SubscriptionTier,
    val maxQueues: Int,
    val maxOperatorsPerQueue: Int,
    val maxTicketsPerDay: Int,
    val maxActiveTickets: Int,
    val maxInvitesPerMonth: Int,
    val canUseEmailNotifications: Boolean,
    val canUseCustomBranding: Boolean,
    val canUseAnalytics: Boolean,
    val canUseApiAccess: Boolean,
    val updatedAt: Instant,
    val updatedBy: String?,
) {
    fun isUnlimited(value: Int): Boolean = value == UNLIMITED
    
    companion object {
        const val UNLIMITED = -1
    }
}
```

**1.3 Repository interface (`TierLimitRepository`)**
```kotlin
interface TierLimitRepository {
    fun findByTier(tier: SubscriptionTier): TierLimit?
    fun findAll(): List<TierLimit>
    fun save(limit: TierLimit)
}
```

### Fase 2: Infrastructure

**2.1 JDBC Repository (`JdbcTierLimitRepository.kt`)**
- Implementer `TierLimitRepository` med Spring JdbcTemplate
- Caching med in-memory cache (refresh ved save)

**2.2 UseCaseConfig oppdatering**
- Wire opp repository i Spring-konfig

### Fase 3: Application Layer

**3.1 Refaktorer `SubscriptionService`**
- Fjern hardkodet `SubscriptionLimits.forTier()`
- Hent limits fra `TierLimitRepository`
- Fallback til hardkodet hvis DB tom (defensive)

**3.2 Nye use cases**
- `GetTierLimitsUseCase` - list alle tier limits
- `UpdateTierLimitUseCase` - oppdater en tier's limits

**3.3 Utvid enforcement**
- `canIssueTicket()` - sjekk tickets/dag og aktive tickets
- `canSendInvite()` - sjekk invites/måned

### Fase 4: API & Controller

**4.1 TierLimitsController**
```kotlin
@RestController
@RequestMapping("/api/admin/tier-limits")
@PreAuthorize("hasRole('SUPERADMIN')")
class TierLimitsController {
    @GetMapping
    fun getAll(): List<TierLimitDTO>
    
    @GetMapping("/{tier}")
    fun getByTier(@PathVariable tier: String): TierLimitDTO
    
    @PutMapping("/{tier}")
    fun update(@PathVariable tier: String, @RequestBody request: UpdateTierLimitRequest): TierLimitDTO
}
```

### Fase 5: UI

**5.1 Thymeleaf template (`admin-tier-limits.html`)**
- Tabell med alle tiers og deres limits
- Edit-form for å endre verdier
- Viser "Ubegrenset" for -1 verdier

**5.2 WebController route**
- `GET /admin/tier-limits` → render template

### Fase 6: Quota Display i eksisterende UI

**6.1 Dashboard endringer**
- Vis "3 av 10 køer brukt" på dashboard
- Vis "Oppgrader for flere" når nærme limit

**6.2 Subscription info endpoint**
```kotlin
@GetMapping("/api/subscription/usage")
fun getUsage(): SubscriptionUsageDTO {
    // Returns current usage vs limits
}
```

## Filer som må endres/opprettes

### Nye filer
```
infrastructure/src/main/resources/db/migration/V14__add_tier_limits.sql
domain/src/main/kotlin/.../model/TierLimit.kt
domain/src/main/kotlin/.../port/Repositories.kt (legg til TierLimitRepository)
infrastructure/src/.../adapter/persistence/JdbcTierLimitRepository.kt
infrastructure/src/.../controller/TierLimitsController.kt
infrastructure/src/main/resources/templates/admin-tier-limits.html
```

### Endrede filer
```
application/src/.../service/SubscriptionService.kt (refaktorering)
infrastructure/src/.../config/UseCaseConfig.kt (wire repository)
infrastructure/src/.../controller/WebController.kt (ny route)
infrastructure/src/.../config/SecurityConfig.kt (tillat admin route)
```

## Testing

1. **Unit tests**
   - `TierLimitRepositoryTest` - JDBC operasjoner
   - `SubscriptionServiceTest` - limits fra DB

2. **Integration tests**
   - API endpoints med mock superadmin
   - Enforcement ved queue/operator/ticket creation

## Estimert arbeidstid

| Fase | Estimat |
|------|---------|
| Fase 1: Database & Domain | 30 min |
| Fase 2: Infrastructure | 30 min |
| Fase 3: Application | 45 min |
| Fase 4: API | 30 min |
| Fase 5: UI | 45 min |
| Fase 6: Quota display | 30 min |
| Testing | 30 min |
| **Total** | **~4 timer** |

## Åpne spørsmål

1. ~~Skal vi ha audit log for limit-endringer?~~ → Implementeres med `updated_at` og `updated_by` felt
2. Skal invites telles per bruker eller per kø? → Per bruker (globalt)
3. Trenger vi rate limiting på API-endepunktet? → Nei, kun superadmin

## Neste steg

1. ✅ Opprett branch `feature/plan-limits`
2. ✅ Implementer Fase 1 (Database & Domain)
3. ✅ Implementer Fase 2 (Infrastructure - JdbcTierLimitRepository)
4. ✅ Implementer Fase 3 (Application - SubscriptionService refaktorering)
5. ✅ Implementer Fase 4 (API - TierLimitsController)
6. ✅ Implementer Fase 5 (UI - admin-tier-limits.html)
7. ⏳ Fase 6: Quota display i eksisterende UI (utsatt til neste iterasjon)
8. ⏳ Tester (Java 21 toolchain ikke tilgjengelig i sandbox)
9. ⏳ PR med automerge
