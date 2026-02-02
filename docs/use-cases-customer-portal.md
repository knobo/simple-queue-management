# Use Cases: Customer Portal

## Oversikt

Customer Portal er landingssiden for innloggede brukere som ikke eier eller opererer noen køer. Dette er typisk kunder som har tatt billetter i andres køer, og gir dem en dedikert plass for å se sin status og historikk.

Portalen fungerer også som onboarding-punkt for å konvertere kunder til kø-eiere.

## Roller

| Rolle | Beskrivelse | Tilgang |
|-------|-------------|---------|
| **Customer** | Innlogget bruker uten egne køer | Customer Portal |
| **Owner/Operator** | Bruker med minst én kø | Dashboard (bypasser Customer Portal) |
| **Guest** | Ikke-innlogget bruker | Kun public ticket-status |

---

## Routing-logikk

```
Bruker logger inn
    ↓
GetMyQueues()
    ↓
┌─────────────────────┐
│ Har køer?           │
└─────────────────────┘
    │           │
   Ja          Nei
    ↓           ↓
Dashboard    Customer Portal
```

---

## UC-CP-01: Se Customer Portal (Landing)

**Aktør:** Customer (innlogget bruker uten køer)  
**Forutsetning:** Bruker er innlogget, har ingen køer (eier eller operator)

### Hovedflyt
1. Bruker logger inn via Keycloak
2. System kaller `GetMyQueues(userId)`
3. Resultatet er tomt → redirect til Customer Portal
4. System henter:
   - Aktiv billett (hvis noen) via `GetActiveTicketForUser(userId)`
   - Ticket-historikk via `GetTicketHistoryForUser(userId)`
5. Portal vises med:
   - Aktiv billett-kort (prominent, øverst)
   - Historikk-liste (nyeste først)
   - CTA: "Opprett egen kø"

### Alternative flyter
- **3a.** Bruker har køer → redirect til Dashboard
- **4a.** Ingen aktiv billett → vis "Du har ingen aktiv billett" med hint om hvordan få en

### Resultat
Customer ser sin status og kan navigere videre

---

## UC-CP-02: Se Aktiv Billett

**Aktør:** Customer  
**Forutsetning:** Bruker har en aktiv billett (status: WAITING eller CALLED)

### Hovedflyt
1. Customer Portal laster
2. System finner aktiv billett knyttet til brukerens userId
3. Billett-kort viser:
   - Kø-navn
   - Billettnummer (f.eks. "A-042")
   - Nåværende status (WAITING/CALLED)
   - Posisjon i kø (hvis WAITING)
   - Estimert ventetid (hvis tilgjengelig)
4. Real-time oppdatering via SSE/polling

### Alternative flyter
- **2a.** Ingen aktiv billett: Vis placeholder "Du har ingen aktiv billett for øyeblikket"
- **3a.** Status er CALLED: Vis prominent "DU ER KALT! Gå til skranken"

### Resultat
Customer ser live status på sin billett

---

## UC-CP-03: Se Ticket-historikk

**Aktør:** Customer  
**Forutsetning:** Bruker har tidligere hatt billetter

### Hovedflyt
1. Customer Portal viser historikk-seksjon
2. System henter alle COMPLETED/CANCELLED billetter for userId
3. Liste viser per billett:
   - Kø-navn
   - Dato
   - Billettnummer
   - Status (Fullført/Kansellert)
   - Ventetid (fra issuedAt til servedAt)
4. Paginering hvis mange billetter (20 per side)

### Alternative flyter
- **2a.** Ingen historikk: Vis "Du har ingen tidligere billetter"

### Resultat
Customer kan se sin besøkshistorikk

---

## UC-CP-04: Gå til Opprett Kø

**Aktør:** Customer  
**Forutsetning:** Bruker ønsker å opprette egen kø

### Hovedflyt
1. Customer klikker "Opprett egen kø" CTA
2. System redirecter til `/create-queue` eller `/dashboard/new-queue`
3. Bruker følger standard CreateQueue-flow
4. Etter opprettelse: Bruker er nå Owner, fremtidige logins → Dashboard

### Resultat
Customer konvertert til Owner

---

## UC-CP-05: Lenke til Billett-detaljer

**Aktør:** Customer  
**Forutsetning:** Bruker vil se mer info om en billett (aktiv eller historisk)

### Hovedflyt
1. Customer klikker på en billett i portal
2. System redirecter til ticket-status-side (`/ticket/{ticketId}`)
3. Bruker ser full billett-info:
   - QR-kode (for aktive)
   - Kø-info
   - Tidsstempler
   - Eventuell notifikasjonshistorikk

### Resultat
Customer ser detaljert billett-visning

---

## Datamodell

### Eksisterende modeller som brukes

```kotlin
// Ticket (eksisterer allerede)
data class Ticket(
    val id: UUID,
    val queueId: UUID,
    val userId: String?,  // Keycloak subject - kobler ticket til bruker
    val number: String,
    val status: TicketStatus,
    val name: String?,
    val email: String?,
    val issuedAt: Instant,
    val calledAt: Instant?,
    val servedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?
)
```

### Ny DTO: TicketHistoryItem

```kotlin
data class TicketHistoryItem(
    val ticketId: UUID,
    val queueName: String,
    val ticketNumber: String,
    val status: TicketStatus,
    val issuedAt: Instant,
    val completedAt: Instant?,
    val waitTimeMinutes: Int?
)
```

### Ny DTO: ActiveTicketView

```kotlin
data class ActiveTicketView(
    val ticketId: UUID,
    val queueId: UUID,
    val queueName: String,
    val ticketNumber: String,
    val status: TicketStatus,
    val positionInQueue: Int?,  // Null hvis CALLED
    val estimatedWaitMinutes: Int?,
    val issuedAt: Instant,
    val calledAt: Instant?
)
```

### Ny DTO: CustomerPortalView

```kotlin
data class CustomerPortalView(
    val activeTicket: ActiveTicketView?,
    val history: List<TicketHistoryItem>,
    val hasMoreHistory: Boolean,
    val totalHistoryCount: Int
)
```

---

## API Endpoints

| Method | Endpoint | Beskrivelse | Auth |
|--------|----------|-------------|------|
| GET | `/api/me/queues` | Sjekk om bruker har køer (routing) | Authenticated |
| GET | `/api/me/portal` | Hent Customer Portal data | Authenticated |
| GET | `/api/me/active-ticket` | Hent aktiv billett (hvis noen) | Authenticated |
| GET | `/api/me/ticket-history` | Hent ticket-historikk | Authenticated |
| GET | `/api/me/ticket-history?page=2` | Paginert historikk | Authenticated |

### Response eksempel: `/api/me/portal`

```json
{
  "activeTicket": {
    "ticketId": "123e4567-e89b-12d3-a456-426614174000",
    "queueId": "987fcdeb-51a2-3bc4-d567-426614174000",
    "queueName": "Bakeri Nordmann",
    "ticketNumber": "A-042",
    "status": "WAITING",
    "positionInQueue": 3,
    "estimatedWaitMinutes": 12,
    "issuedAt": "2025-01-30T10:15:00Z",
    "calledAt": null
  },
  "history": [
    {
      "ticketId": "abc12345-...",
      "queueName": "Vinmonopolet Sentrum",
      "ticketNumber": "B-017",
      "status": "COMPLETED",
      "issuedAt": "2025-01-28T14:30:00Z",
      "completedAt": "2025-01-28T14:52:00Z",
      "waitTimeMinutes": 22
    }
  ],
  "hasMoreHistory": true,
  "totalHistoryCount": 47
}
```

---

## Service Interface

```kotlin
interface CustomerPortalService {
    fun getPortalView(userId: String): CustomerPortalView
    fun getActiveTicket(userId: String): ActiveTicketView?
    fun getTicketHistory(userId: String, page: Int = 0, size: Int = 20): Page<TicketHistoryItem>
    fun hasActiveTicket(userId: String): Boolean
}
```

---

## Frontend Routing Logic

```typescript
// Efter innlogging (i auth callback eller layout)
async function routeAfterLogin(userId: string): Promise<string> {
  const queues = await api.getMyQueues();
  
  if (queues.length > 0) {
    return '/dashboard';
  } else {
    return '/portal';  // Customer Portal
  }
}
```

---

## Sikkerhetshensyn

1. **Bruker kan kun se egne billetter**: Alle queries filtrerer på `userId`
2. **Ingen sensitiv kø-data eksponeres**: Kun kø-navn, ikke interne settings
3. **Rate limiting på historikk**: Hindre scraping av ticket-data
4. **Ticket-detaljer**: Kun tilgjengelig for ticket-owner eller kø-owner

---

## UI-skisser

### Customer Portal - Med aktiv billett
```
┌────────────────────────────────────────┐
│  Simple Queue                    👤    │
├────────────────────────────────────────┤
│                                        │
│  ┌────────────────────────────────┐    │
│  │ 🎫 Aktiv billett               │    │
│  │                                │    │
│  │ Bakeri Nordmann                │    │
│  │                                │    │
│  │       A-042                    │    │
│  │       VENTER                   │    │
│  │                                │    │
│  │ Posisjon: 3 i køen            │    │
│  │ Estimert ventetid: ~12 min     │    │
│  │                                │    │
│  │ [Se detaljer]                  │    │
│  └────────────────────────────────┘    │
│                                        │
├────────────────────────────────────────┤
│  📋 Tidligere billetter               │
├────────────────────────────────────────┤
│  Vinmonopolet Sentrum                  │
│  B-017 · 28. jan · 22 min ventetid     │
│                                        │
│  Legekontoret Vest                     │
│  C-003 · 25. jan · 8 min ventetid      │
│                                        │
│  [Vis mer historikk]                   │
├────────────────────────────────────────┤
│                                        │
│  Vil du ha din egen kø?                │
│  [✨ Opprett egen kø]                  │
│                                        │
└────────────────────────────────────────┘
```

### Customer Portal - Uten aktiv billett
```
┌────────────────────────────────────────┐
│  Simple Queue                    👤    │
├────────────────────────────────────────┤
│                                        │
│  ┌────────────────────────────────┐    │
│  │ 🎫 Ingen aktiv billett         │    │
│  │                                │    │
│  │ Du har ingen billett akkurat   │    │
│  │ nå. Scan en QR-kode hos en     │    │
│  │ bedrift for å ta kølapp.       │    │
│  └────────────────────────────────┘    │
│                                        │
├────────────────────────────────────────┤
│  📋 Tidligere billetter               │
├────────────────────────────────────────┤
│  Vinmonopolet Sentrum                  │
│  B-017 · 28. jan · Fullført            │
│                                        │
│  [Vis mer]                             │
├────────────────────────────────────────┤
│                                        │
│  ┌────────────────────────────────┐    │
│  │ Driver du en bedrift?          │    │
│  │                                │    │
│  │ Opprett din egen kø og la      │    │
│  │ kundene dine slippe å vente    │    │
│  │ fysisk i lokalet.              │    │
│  │                                │    │
│  │ [✨ Kom i gang gratis]         │    │
│  └────────────────────────────────┘    │
│                                        │
└────────────────────────────────────────┘
```

### Aktiv billett - CALLED status
```
┌────────────────────────────────────────┐
│  ┌────────────────────────────────┐    │
│  │ 🔔 DU ER KALT!                 │    │
│  │                                │    │
│  │ Bakeri Nordmann                │    │
│  │                                │    │
│  │       A-042                    │    │
│  │                                │    │
│  │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │    │
│  │   Gå til skranken nå!         │    │
│  │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │    │
│  │                                │    │
│  │ Kalt kl 14:32                  │    │
│  └────────────────────────────────┘    │
└────────────────────────────────────────┘
```
