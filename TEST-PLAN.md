# Simple Queue — Integrasjonstest Plan

*Opprettet: 2026-02-01*

## Oversikt

Denne planen dekker integrasjonstester for hele Simple Queue-systemet. Vi bruker:
- **Testcontainers** for PostgreSQL
- **Mock JWT** eller **Keycloak Testcontainer** for autentisering
- **Spring Boot Test** med `@SpringBootTest`

---

## Keycloak Test-brukere

### Roller i systemet
| Rolle | Beskrivelse |
|-------|-------------|
| `SUPERADMIN` | Full admin-tilgang (tier limits, sales admin, feedback) |
| `SELLER` | Selger-tilgang (referral dashboard) |
| `QUEUE_OWNER` | Eier av køer (standard bruker) |
| (ingen rolle) | Vanlig kunde som bruker køen |

### Test-brukere i Keycloak (faktiske)

| Bruker | Rolle | Keycloak ID |
|--------|-------|-------------|
| `bohmer@gmail.com` | superadmin | a53ef32d-98f4-41bf-a55f-f0d0d0c514bf |
| `owner-cafe@test.queue.knobo.no` | queue-owner | 74abf2f8-7466-4202-ba9f-c29480a4401c |
| `owner-clinic@test.queue.knobo.no` | queue-owner | 9c42caff-fc15-4c58-b916-bd0389b3dad7 |
| `owner-salon@test.queue.knobo.no` | queue-owner | cefbb850-67ec-43a9-910a-b3fb04dbea9f |
| `seller-anna@test.queue.knobo.no` | seller | 4272d77a-1ca6-451e-9fa5-0ceae50edc7c |
| `seller-bob@test.queue.knobo.no` | seller | d34f4e00-d843-48aa-a283-101235c14ad1 |
| `customer-per@test.queue.knobo.no` | (ingen) | d011c1a3-8d8b-4664-8067-f4fb0f2c9858 |
| `customer-ole@test.queue.knobo.no` | (ingen) | 69e1078d-cfb9-42b5-9eb6-e87ec712c512 |
| `customer-kari@test.queue.knobo.no` | (ingen) | fd5301b2-063c-49cd-a789-cb0ebc8dc39d |

**Keycloak URL:** https://login.knobo.no
**Realm:** simple-queue

---

## Test-kategorier

### 1. 🔐 Autentisering & Autorisasjon

| Test | Prioritet | Status |
|------|-----------|--------|
| Uautorisert bruker får 401 på beskyttede endpoints | HØY | ⏳ |
| SUPERADMIN kan aksessere `/admin/*` | HØY | ⏳ |
| Vanlig bruker får 403 på admin-endpoints | HØY | ⏳ |
| SELLER kan aksessere `/seller/dashboard` | MEDIUM | ⏳ |
| Kø-eier kan kun se egne køer | HØY | ⏳ |
| Operatør kan administrere kø de er medlem av | HØY | ⏳ |

### 2. 📋 Kø-administrasjon (Owner)

| Test | Prioritet | Status |
|------|-----------|--------|
| Opprett ny kø | HØY | ⏳ |
| List egne køer (`/queues/me`) | HØY | ⏳ |
| Åpne/lukke kø | HØY | ⏳ |
| Slett kø | MEDIUM | ⏳ |
| Legg til custom queue state | MEDIUM | ⏳ |
| Fjern queue state | MEDIUM | ⏳ |

### 3. 🎫 Billett-flyt (Core)

| Test | Prioritet | Status |
|------|-----------|--------|
| Kunde tar billett med QR-kode secret | HØY | ✅ |
| Kunde kan ikke ta billett når kø er stengt | HØY | ⏳ |
| Eier kaller neste billett (`/queues/{id}/next`) | HØY | ⏳ |
| Server billett (`/queues/{id}/tickets/{id}/serve`) | HØY | ⏳ |
| Fullfør billett (`/queues/{id}/tickets/{id}/complete`) | HØY | ⏳ |
| Slett/avbryt billett | MEDIUM | ⏳ |
| Billettnummer inkrementerer riktig | HØY | ⏳ |

### 4. 🔗 Access Tokens (QR-koder)

| Test | Prioritet | Status |
|------|-----------|--------|
| ONE_TIME token kan kun brukes én gang | HØY | ✅ |
| ROTATING token regenereres etter utløp | HØY | ⏳ |
| Ugyldig token gir feilmelding | HØY | ⏳ |
| Token status endpoint (`/queues/{id}/token/status`) | MEDIUM | ⏳ |

### 5. 👥 Invitasjoner & Operatører

| Test | Prioritet | Status |
|------|-----------|--------|
| Eier kan invitere operatør | HØY | ⏳ |
| Operatør kan akseptere invitasjon | HØY | ⏳ |
| Operatør kan avslå invitasjon | MEDIUM | ⏳ |
| Operatør kan administrere kø etter aksept | HØY | ⏳ |
| Eier kan fjerne operatør | MEDIUM | ⏳ |
| Tier-limit på antall operatører enforces | MEDIUM | ✅ |

### 6. 💳 Abonnement & Tier Limits

| Test | Prioritet | Status |
|------|-----------|--------|
| FREE tier: maks 1 kø | HØY | ✅ |
| FREE tier: maks 50 billetter/dag | HØY | ✅ |
| Oppgradering til PRO øker limits | MEDIUM | ⏳ |
| Superadmin kan endre tier limits | MEDIUM | ⏳ |

### 7. 📊 Kunde-portal

| Test | Prioritet | Status |
|------|-----------|--------|
| Kunde ser aktiv billett | HØY | ⏳ |
| Kunde ser billetthistorikk | MEDIUM | ⏳ |
| Billettstatus oppdateres via SSE | HØY | ⏳ |

### 8. 💬 Feedback

| Test | Prioritet | Status |
|------|-----------|--------|
| Bruker kan sende feedback | MEDIUM | ⏳ |
| Bruker ser egen feedback | MEDIUM | ⏳ |
| Superadmin ser all feedback | MEDIUM | ⏳ |
| Superadmin kan endre status | MEDIUM | ⏳ |

### 9. 💰 Selger & Referrals

| Test | Prioritet | Status |
|------|-----------|--------|
| Referral-kode settes ved signup | MEDIUM | ⏳ |
| Selger ser sine koblede kunder | MEDIUM | ⏳ |
| Superadmin kan opprette selger | MEDIUM | ⏳ |
| Superadmin kan koble kunde til selger manuelt | MEDIUM | ⏳ |

---

## Test-infrastruktur

### Nåværende oppsett
```kotlin
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestEmailConfig::class)
class MyIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
```

### Forbedret oppsett med Mock JWT

```kotlin
// TestSecurityConfig.kt - Forbedret versjon
@TestConfiguration
class TestSecurityConfig {
    @Bean
    @Primary
    fun testFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(mockJwtDecoder())
                }
            }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/public/**").permitAll()
                auth.requestMatchers("/admin/**").hasRole("SUPERADMIN")
                auth.requestMatchers("/seller/**").hasRole("SELLER")
                auth.anyRequest().authenticated()
            }
        return http.build()
    }
}

// TestJwtHelper.kt - Generer mock JWT tokens
object TestJwtHelper {
    fun createToken(userId: String, roles: List<String> = emptyList()): Jwt {
        return Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .subject(userId)
            .claim("preferred_username", userId)
            .claim("realm_access", mapOf("roles" to roles))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }
    
    fun superadminToken() = createToken("superadmin", listOf("SUPERADMIN"))
    fun sellerToken() = createToken("seller", listOf("SELLER"))
    fun ownerToken(id: String = "owner1") = createToken(id)
}
```

### Med MockMvc

```kotlin
@Test
fun `owner can create queue`() {
    mockMvc.perform(
        post("/api/queues")
            .with(jwt().jwt(TestJwtHelper.ownerToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name": "My Queue"}""")
    )
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.name").value("My Queue"))
}

@Test
fun `non-admin cannot access admin endpoints`() {
    mockMvc.perform(
        get("/admin/tier-limits")
            .with(jwt().jwt(TestJwtHelper.ownerToken()))
    )
        .andExpect(status().isForbidden)
}
```

---

## Implementasjonsrekkefølge

### Sprint 1: Grunnleggende (Uke 1)
1. ✅ Sett opp `TestSecurityConfig` med mock JWT
2. ⏳ Lag `TestJwtHelper` for å generere tokens
3. ⏳ Test: Autentisering basics (401, 403)
4. ⏳ Test: Kø CRUD operasjoner

### Sprint 2: Kjerneflyt (Uke 2)
5. ⏳ Test: Komplett billett-flyt (ta, kall, server, fullfør)
6. ⏳ Test: Access tokens (ONE_TIME, ROTATING)
7. ⏳ Test: Invitasjoner og operatører

### Sprint 3: Avansert (Uke 3)
8. ⏳ Test: Tier limits enforcement
9. ⏳ Test: Kunde-portal og SSE
10. ⏳ Test: Feedback system
11. ⏳ Test: Selger/referral system

---

## Filstruktur

```
infrastructure/src/test/kotlin/com/example/simplequeue/infrastructure/
├── TestSecurityConfig.kt          # Mock security
├── TestEmailConfig.kt             # Mock email
├── TestJwtHelper.kt               # JWT token generator (NY)
├── DatabaseSchemaTest.kt          # Schema validation
├── IssueTicketIntegrationTest.kt  # Billett-tester
├── TierLimitsEnforcementIntegrationTest.kt
├── auth/
│   └── AuthorizationIntegrationTest.kt (NY)
├── queue/
│   ├── QueueCrudIntegrationTest.kt (NY)
│   └── QueueOperationsIntegrationTest.kt (NY)
├── ticket/
│   ├── TicketFlowIntegrationTest.kt (NY)
│   └── AccessTokenIntegrationTest.kt (NY)
├── invite/
│   └── InviteIntegrationTest.kt (NY)
├── subscription/
│   └── SubscriptionIntegrationTest.kt (NY)
└── e2e/
    └── LandingPageE2ETest.kt
```

---

## Avhengigheter som trengs

```kotlin
// build.gradle.kts
testImplementation("org.springframework.security:spring-security-test")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:postgresql")
```

---

## Neste steg

1. **Finn Keycloak test-brukere** som ble opprettet
2. **Implementer `TestJwtHelper`** for mock tokens
3. **Start med `AuthorizationIntegrationTest`** - grunnleggende auth-tester
4. **Fortsett med `QueueCrudIntegrationTest`** - kø CRUD

---

*Spørsmål? Ping Astra 🛡️*
