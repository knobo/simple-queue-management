# Use Cases: Feedback System

## Oversikt

Feedback-systemet lar innloggede brukere rapportere bugs, foreslå nye features, eller gi generell tilbakemelding. Dette gir verdifull innsikt i brukeropplevelsen og hjelper med å prioritere utviklingsarbeid.

All feedback krever innlogging for å:
- Unngå spam
- Kunne følge opp med bruker
- Samle kontekst (subscription tier, brukertype)

## Roller

| Rolle | Beskrivelse | Rettigheter |
|-------|-------------|-------------|
| **User** | Innlogget bruker (Customer, Owner, Operator) | Opprette feedback, se egne submissions |
| **Admin** | System-administrator | Se all feedback, endre status, respondere |

---

## Feedback-typer

| Type | Beskrivelse | Eksempel |
|------|-------------|----------|
| **BUG** | Noe fungerer ikke som forventet | "Knappen 'Neste' svarer ikke på mobil" |
| **FEATURE** | Forslag til ny funksjonalitet | "Ønske om SMS-varsling" |
| **GENERAL** | Annen tilbakemelding | "Fin app, men litt treg lasting" |

---

## Kategorier

Kategorier hjelper med å organisere og prioritere feedback. Bruker velger én kategori ved innsending.

| Kategori | Beskrivelse | Ikon |
|----------|-------------|------|
| **QUEUE_MANAGEMENT** | Kø-håndtering, billetter, serving | 🎫 |
| **NOTIFICATIONS** | Varsler, e-post, SMS | 🔔 |
| **DASHBOARD** | Dashboard, statistikk, oversikt | 📊 |
| **BILLING** | Betaling, subscription, faktura | 💳 |
| **MOBILE** | Mobilapp, responsivt design | 📱 |
| **PERFORMANCE** | Hastighet, lasting, responstid | ⚡ |
| **USABILITY** | Brukervennlighet, UX, design | 🎨 |
| **INTEGRATIONS** | API, webhooks, tredjeparter | 🔌 |
| **OTHER** | Annet som ikke passer kategoriene | 📦 |

Admin kan også legge til egendefinerte tags i etterkant for mer granulær organisering.

---

## UC-FB-01: Send Feedback

**Aktør:** User (innlogget)  
**Forutsetning:** Bruker er autentisert

### Hovedflyt
1. Bruker navigerer til "Gi tilbakemelding" (via meny, footer, eller hjelp-seksjon)
2. System viser feedback-skjema med:
   - Type (dropdown: Bug report, Feature request, General feedback)
   - Kategori (dropdown: Queue Management, Notifications, Dashboard, etc.)
   - Tittel (obligatorisk, maks 100 tegn)
   - Beskrivelse (obligatorisk, maks 2000 tegn)
   - Screenshot URL (valgfritt)
3. Bruker fyller ut skjema
4. Bruker klikker "Send"
5. System validerer input
6. System lagrer feedback med:
   - Brukerinfo (userId, email)
   - Type og kategori
   - Kontekst (subscription tier, user agent, current URL)
   - Timestamp
7. System viser bekreftelse: "Takk for din tilbakemelding!"
8. (Valgfritt) System sender notifikasjon til admin

### Alternative flyter
- **5a.** Validering feiler: Vis feilmeldinger inline
- **6a.** Rate limit nådd: Vis "Du har sendt for mange tilbakemeldinger. Prøv igjen senere."

### Resultat
Feedback lagret i systemet for admin-gjennomgang

---

## UC-FB-02: Se Mine Tilbakemeldinger

**Aktør:** User  
**Forutsetning:** Bruker har sendt minst én tilbakemelding

### Hovedflyt
1. Bruker går til "Mine tilbakemeldinger" (under profil/settings)
2. System henter alle feedback submissions for userId
3. Liste viser:
   - Type-ikon (🐛/💡/💬)
   - Tittel
   - Status (Mottatt, Under behandling, Løst, Lukket)
   - Dato
4. Bruker kan klikke for å se detaljer

### Alternative flyter
- **2a.** Ingen submissions: Vis "Du har ikke sendt noen tilbakemeldinger ennå"

### Resultat
Bruker ser oversikt over sine submissions og deres status

---

## UC-FB-03: Admin: Se All Feedback

**Aktør:** Admin  
**Forutsetning:** Bruker har admin-rolle

### Hovedflyt
1. Admin går til Admin Panel → Feedback
2. System henter all feedback med filtre:
   - Type (alle, BUG, FEATURE, GENERAL)
   - Kategori (alle, QUEUE_MANAGEMENT, NOTIFICATIONS, etc.)
   - Status (alle, NEW, IN_PROGRESS, RESOLVED, CLOSED)
   - Tags (admin-definerte)
   - Dato-range
   - Søk i tittel/beskrivelse
3. Liste viser per item:
   - Type
   - Kategori
   - Tittel
   - Brukerinfo (e-post, subscription tier)
   - Opprettet dato
   - Status
4. Admin kan sortere på alle kolonner

### Resultat
Admin har oversikt over all bruker-feedback

---

## UC-FB-04: Admin: Behandle Feedback

**Aktør:** Admin  
**Forutsetning:** Det finnes feedback å behandle

### Hovedflyt
1. Admin klikker på en feedback-item
2. System viser full detaljer:
   - All bruker-innsendt info
   - System-kontekst (browser, URL, tier)
   - Screenshot (hvis vedlagt)
   - Intern notat-historikk
3. Admin kan:
   - Endre status
   - Legge til intern notat
   - (Fremtidig) Sende respons til bruker
4. Endringer lagres med audit trail

### Resultat
Feedback oppdatert med ny status/notater

---

## UC-FB-05: Admin: Eksporter Feedback

**Aktør:** Admin  
**Forutsetning:** Admin ønsker å analysere feedback eksternt

### Hovedflyt
1. Admin velger filtre i feedback-listen
2. Admin klikker "Eksporter"
3. System genererer CSV med valgt data
4. Fil lastes ned

### Resultat
Admin har feedback-data for analyse

---

## UC-FB-06: Admin: Administrere Tags

**Aktør:** Admin  
**Forutsetning:** Det finnes feedback å tagge

### Hovedflyt
1. Admin åpner en feedback-item
2. Admin klikker "Legg til tag"
3. System viser:
   - Input-felt med autocomplete fra eksisterende tags
   - Liste over populære/nylige tags
4. Admin skriver inn eller velger tag
5. Tag legges til på feedback
6. Endring logges i audit trail

### Alternative flyter
- **4a.** Tag finnes ikke: Ny tag opprettes automatisk
- **4b.** Admin fjerner tag: Klikk × på eksisterende tag

### Regler for tags
- Lowercase, alphanumeric + bindestrek
- Maks 30 tegn per tag
- Maks 10 tags per feedback
- Eksempler: `mobile`, `critical`, `ux-bug`, `android-13`

### Resultat
Feedback er tagget for bedre organisering og filtrering

---

## Datamodell

### Feedback

```kotlin
data class Feedback(
    val id: UUID,
    val userId: String,  // Keycloak subject
    val userEmail: String,
    val type: FeedbackType,
    val category: FeedbackCategory,
    val tags: Set<String>,  // Admin-definerte tags
    val title: String,
    val description: String,
    val screenshotUrl: String?,
    val status: FeedbackStatus,
    
    // Kontekst samlet ved innsending
    val context: FeedbackContext,
    
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?
)

enum class FeedbackType {
    BUG,
    FEATURE,
    GENERAL
}

enum class FeedbackCategory {
    QUEUE_MANAGEMENT,  // Kø-håndtering, billetter, serving
    NOTIFICATIONS,     // Varsler, e-post, SMS
    DASHBOARD,         // Dashboard, statistikk, oversikt
    BILLING,           // Betaling, subscription, faktura
    MOBILE,            // Mobilapp, responsivt design
    PERFORMANCE,       // Hastighet, lasting, responstid
    USABILITY,         // Brukervennlighet, UX, design
    INTEGRATIONS,      // API, webhooks, tredjeparter
    OTHER              // Annet
}

enum class FeedbackStatus {
    NEW,          // Nettopp mottatt
    IN_PROGRESS,  // Under behandling
    RESOLVED,     // Løst/implementert
    CLOSED,       // Lukket (ikke fikset/avvist)
    DUPLICATE     // Duplikat av annen feedback
}
```

### FeedbackContext

```kotlin
data class FeedbackContext(
    val userAgent: String?,
    val currentUrl: String?,
    val subscriptionTier: SubscriptionTier,
    val userRole: UserRole,  // CUSTOMER, OWNER, OPERATOR
    val queueCount: Int,     // Antall køer bruker har
    val appVersion: String?
)

enum class UserRole {
    CUSTOMER,  // Ingen køer
    OWNER,     // Eier minst én kø
    OPERATOR   // Operator på minst én kø (ikke owner)
}
```

### FeedbackNote (Admin-notater)

```kotlin
data class FeedbackNote(
    val id: UUID,
    val feedbackId: UUID,
    val adminUserId: String,
    val note: String,
    val createdAt: Instant
)
```

---

## API Endpoints

### User Endpoints

| Method | Endpoint | Beskrivelse | Auth |
|--------|----------|-------------|------|
| POST | `/api/feedback` | Send ny feedback | Authenticated |
| GET | `/api/feedback/mine` | Hent mine submissions | Authenticated |
| GET | `/api/feedback/mine/{id}` | Hent én submission | Authenticated |
| GET | `/api/feedback/categories` | Hent tilgjengelige kategorier | Authenticated |

### Admin Endpoints

| Method | Endpoint | Beskrivelse | Auth |
|--------|----------|-------------|------|
| GET | `/api/admin/feedback` | List all feedback (paginert) | Admin |
| GET | `/api/admin/feedback/{id}` | Hent feedback detaljer | Admin |
| PATCH | `/api/admin/feedback/{id}` | Oppdater status | Admin |
| POST | `/api/admin/feedback/{id}/notes` | Legg til notat | Admin |
| POST | `/api/admin/feedback/{id}/tags` | Legg til tags | Admin |
| DELETE | `/api/admin/feedback/{id}/tags` | Fjern tags | Admin |
| GET | `/api/admin/feedback/tags` | List alle brukte tags | Admin |
| GET | `/api/admin/feedback/export` | Eksporter som CSV | Admin |
| GET | `/api/admin/feedback/stats` | Statistikk-oversikt | Admin |

### Request/Response eksempler

#### POST `/api/feedback`

Request:
```json
{
  "type": "BUG",
  "category": "QUEUE_MANAGEMENT",
  "title": "Knappen 'Neste kunde' fungerer ikke",
  "description": "Når jeg klikker på knappen skjer det ingenting. Prøvd flere ganger. Bruker Chrome på mobil.",
  "screenshotUrl": "https://imgur.com/abc123.png"
}
```

Response:
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "status": "NEW",
  "message": "Takk for din tilbakemelding!"
}
```

#### GET `/api/admin/feedback?type=BUG&category=QUEUE_MANAGEMENT&status=NEW&page=0`

Response:
```json
{
  "content": [
    {
      "id": "123e4567-...",
      "type": "BUG",
      "category": "QUEUE_MANAGEMENT",
      "tags": ["mobile", "critical"],
      "title": "Knappen 'Neste kunde' fungerer ikke",
      "userEmail": "bruker@example.com",
      "subscriptionTier": "PRO",
      "status": "NEW",
      "createdAt": "2025-01-30T14:22:00Z"
    }
  ],
  "totalElements": 47,
  "totalPages": 5,
  "page": 0,
  "size": 10
}
```

#### GET `/api/admin/feedback/stats`

Response:
```json
{
  "total": 142,
  "byType": {
    "BUG": 58,
    "FEATURE": 61,
    "GENERAL": 23
  },
  "byCategory": {
    "QUEUE_MANAGEMENT": 42,
    "NOTIFICATIONS": 28,
    "DASHBOARD": 19,
    "BILLING": 15,
    "MOBILE": 12,
    "PERFORMANCE": 8,
    "USABILITY": 7,
    "INTEGRATIONS": 6,
    "OTHER": 5
  },
  "byStatus": {
    "NEW": 12,
    "IN_PROGRESS": 8,
    "RESOLVED": 97,
    "CLOSED": 25
  },
  "topTags": [
    {"tag": "mobile", "count": 34},
    {"tag": "critical", "count": 12},
    {"tag": "ux", "count": 8}
  ],
  "thisWeek": 7,
  "avgResolutionDays": 4.2
}
```

---

## Service Interface

```kotlin
interface FeedbackService {
    // User operations
    fun submit(userId: String, request: FeedbackRequest): Feedback
    fun getMyFeedback(userId: String): List<FeedbackSummary>
    fun getMyFeedbackById(userId: String, feedbackId: UUID): Feedback?
    fun getCategories(): List<FeedbackCategory>  // For dropdown i skjema
    
    // Admin operations
    fun getAll(filter: FeedbackFilter, pageable: Pageable): Page<FeedbackListItem>
    fun getById(feedbackId: UUID): FeedbackDetail?
    fun updateStatus(feedbackId: UUID, status: FeedbackStatus, adminUserId: String)
    fun addNote(feedbackId: UUID, note: String, adminUserId: String)
    fun addTags(feedbackId: UUID, tags: Set<String>, adminUserId: String)
    fun removeTags(feedbackId: UUID, tags: Set<String>, adminUserId: String)
    fun getAllTags(): List<TagCount>  // Alle brukte tags med antall
    fun getStats(): FeedbackStats
    fun export(filter: FeedbackFilter): ByteArray  // CSV
}

data class TagCount(
    val tag: String,
    val count: Int
)

data class FeedbackRequest(
    val type: FeedbackType,
    val category: FeedbackCategory,
    val title: String,
    val description: String,
    val screenshotUrl: String?
)

data class FeedbackFilter(
    val type: FeedbackType?,
    val category: FeedbackCategory?,
    val status: FeedbackStatus?,
    val tags: Set<String>?,
    val fromDate: LocalDate?,
    val toDate: LocalDate?,
    val search: String?
)
```

---

## Validering

| Felt | Regler |
|------|--------|
| `type` | Påkrevd, må være gyldig enum |
| `category` | Påkrevd, må være gyldig enum |
| `title` | Påkrevd, 5-100 tegn |
| `description` | Påkrevd, 20-2000 tegn |
| `screenshotUrl` | Valgfritt, må være gyldig HTTPS URL hvis oppgitt |
| `tags` | Valgfritt (admin), maks 10 tags, hvert tag maks 30 tegn, lowercase alphanumeric + bindestrek |

---

## Rate Limiting

- Maks 5 feedback submissions per bruker per time
- Maks 20 per bruker per dag
- Ved limit: HTTP 429 Too Many Requests

```kotlin
@RateLimited(
    requests = 5, 
    window = Duration.ofHours(1),
    key = "feedback:hourly:{userId}"
)
fun submit(...)
```

---

## Sikkerhetshensyn

1. **Autentisering påkrevd**: Ingen anonym feedback (spam-prevention)
2. **Rate limiting**: Hindre flood av submissions
3. **Input sanitization**: XSS-beskyttelse på tittel/beskrivelse
4. **Screenshot URL validering**: Kun tillat HTTPS, sjekk mot whitelist (imgur, etc.)
5. **Admin-only endpoints**: Sjekk `hasRole('ADMIN')` på alle admin-routes
6. **Audit logging**: Logg alle admin-handlinger
7. **GDPR**: Slett feedback ved brukersletting (eller anonymiser)

---

## UI-skisser

### Feedback-skjema
```
┌────────────────────────────────────────┐
│ Gi oss tilbakemelding                  │
├────────────────────────────────────────┤
│                                        │
│ Hva gjelder det?                       │
│ ┌──────────────────────────────────┐   │
│ │ 🐛 Bug report               ▼   │   │
│ └──────────────────────────────────┘   │
│                                        │
│ Kategori *                             │
│ ┌──────────────────────────────────┐   │
│ │ 🎫 Kø-håndtering            ▼   │   │
│ └──────────────────────────────────┘   │
│                                        │
│ Tittel *                               │
│ ┌──────────────────────────────────┐   │
│ │ Knappen fungerer ikke...         │   │
│ └──────────────────────────────────┘   │
│                                        │
│ Beskrivelse *                          │
│ ┌──────────────────────────────────┐   │
│ │ Når jeg klikker på "Neste"       │   │
│ │ knappen på mobil skjer det       │   │
│ │ ingenting. Bruker Chrome.        │   │
│ │                                  │   │
│ │                                  │   │
│ └──────────────────────────────────┘   │
│                                        │
│ Screenshot URL (valgfritt)             │
│ ┌──────────────────────────────────┐   │
│ │ https://imgur.com/abc123.png     │   │
│ └──────────────────────────────────┘   │
│                                        │
│            [Send tilbakemelding]       │
│                                        │
└────────────────────────────────────────┘
```

### Mine tilbakemeldinger
```
┌────────────────────────────────────────┐
│ Mine tilbakemeldinger                  │
├────────────────────────────────────────┤
│                                        │
│ 🐛 Knappen fungerer ikke               │
│    🎫 Kø-håndtering                    │
│    Status: Under behandling            │
│    Sendt: 30. jan 2025                 │
│                                        │
│ 💡 Ønske om SMS-varsling               │
│    🔔 Varsler                          │
│    Status: Mottatt                     │
│    Sendt: 28. jan 2025                 │
│                                        │
│ 💬 Generelt bra app!                   │
│    🎨 Brukervennlighet                 │
│    Status: Lukket                      │
│    Sendt: 15. jan 2025                 │
│                                        │
├────────────────────────────────────────┤
│ [+ Ny tilbakemelding]                  │
└────────────────────────────────────────┘
```

### Admin Panel - Feedback oversikt
```
┌───────────────────────────────────────────────────────────────────┐
│ Admin > Feedback                                       [Eksporter]│
├───────────────────────────────────────────────────────────────────┤
│ Filtre:                                                           │
│ Type: [Alle ▼]  Kategori: [Alle ▼]  Status: [Nye ▼]              │
│ Tags: [mobile ×] [critical ×] [+ legg til]   Søk: [______] 🔍    │
├───────────────────────────────────────────────────────────────────┤
│ Type │ Kategori     │ Tittel                │ Bruker  │ Status    │
├──────┼──────────────┼───────────────────────┼─────────┼───────────┤
│ 🐛   │ 🎫 Kø        │ Knappen funker ikke   │ PRO     │ 🟡 Ny     │
│ 💡   │ 🔔 Varsler   │ SMS-varsling          │ FREE    │ 🟡 Ny     │
│ 🐛   │ 💳 Betaling  │ Feil ved checkout     │ STARTER │ 🔵 Under  │
│ 💬   │ 🎨 UX        │ Flott app!            │ PRO     │ 🟢 Lukket │
├───────────────────────────────────────────────────────────────────┤
│ Viser 1-10 av 47                              [<] [1] [2] [>]     │
└───────────────────────────────────────────────────────────────────┘
```

### Admin Panel - Feedback detaljer
```
┌──────────────────────────────────────────────────────────┐
│ ← Tilbake                                                │
├──────────────────────────────────────────────────────────┤
│ 🐛 Bug Report  ·  🎫 Kø-håndtering                       │
│ "Knappen fungerer ikke på mobil"                         │
├──────────────────────────────────────────────────────────┤
│ Status: [Under behandling ▼]              [Lagre status] │
├──────────────────────────────────────────────────────────┤
│ Tags: [mobile ×] [critical ×] [+ legg til tag]          │
├──────────────────────────────────────────────────────────┤
│ Beskrivelse:                                             │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Når jeg klikker på "Neste kunde" knappen på mobil    │ │
│ │ skjer det ingenting. Har prøvd flere ganger.         │ │
│ │ Bruker Chrome på Android.                            │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ Screenshot: https://imgur.com/abc123.png [Åpne]         │
├──────────────────────────────────────────────────────────┤
│ Kontekst:                                                │
│ • Bruker: bruker@example.com                            │
│ • Tier: PRO                                             │
│ • Rolle: Owner (2 køer)                                 │
│ • Browser: Chrome 120 / Android 14                      │
│ • URL: /dashboard/queue/123                             │
│ • Sendt: 30. jan 2025 kl 14:22                          │
├──────────────────────────────────────────────────────────┤
│ Interne notater:                                         │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ [Admin] 30. jan: Kan reproduseres. Relatert til      │ │
│ │ issue #142 i GitHub.                                  │ │
│ └──────────────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Legg til notat...                                    │ │
│ └──────────────────────────────────────────────────────┘ │
│                                            [Legg til]    │
└──────────────────────────────────────────────────────────┘
```
