# Use Cases: Invite System

## Oversikt

Invite-systemet lar kø-eiere invitere kundebehandlere (operators) til å jobbe med køen deres. Dette muliggjør team-basert kø-håndtering der flere personer kan serve tickets, uten at alle trenger å være eier.

## Roller

| Rolle | Beskrivelse | Rettigheter |
|-------|-------------|-------------|
| **Owner** | Kø-eier, den som opprettet køen | Full kontroll: serve, complete, revoke, invite, settings, delete |
| **Operator** | Invitert kundebehandler | Serve tickets, complete tickets, se kø-status |
| **Guest** | Bruker med ticket | Se sin egen ticket-status, motta notifikasjoner |

---

## UC-INV-01: Send Invitasjon

**Aktør:** Owner  
**Forutsetning:** Owner har en aktiv kø og tilgjengelige invites (basert på subscription)

### Hovedflyt
1. Owner går til "Team" eller "Invites" i dashboard
2. Owner skriver inn e-postadresse til personen som skal inviteres
3. Owner velger rolle (for nå: kun "Operator")
4. System validerer:
   - E-post er gyldig format
   - Owner har ikke brukt opp sine invites (subscription limit)
   - E-posten er ikke allerede invitert/medlem
5. System oppretter `Invite`-record med unik token
6. System sender e-post med invite-link
7. Invite vises i "Pending invites" listen

### Alternative flyter
- **4a.** Invite-limit nådd: Vis feilmelding "Upgrade subscription for more team members"
- **4b.** Allerede invitert: Vis "This person is already invited. Resend invite?"

### Resultat
Invite opprettet i PENDING status, e-post sendt

---

## UC-INV-02: Aksepter Invitasjon

**Aktør:** Invitert bruker  
**Forutsetning:** Bruker har mottatt invite-e-post med gyldig token

### Hovedflyt
1. Bruker klikker invite-link i e-post
2. System validerer token:
   - Token eksisterer
   - Token ikke utløpt (7 dager TTL)
   - Status er PENDING
3. Hvis bruker ikke er innlogget: Redirect til login/registrer via Keycloak
4. Etter innlogging: System viser "Du er invitert til [kø-navn] som Operator. Aksepter?"
5. Bruker klikker "Aksepter"
6. System oppretter `QueueMember`-record
7. System oppdaterer invite til ACCEPTED
8. Bruker redirectes til operator-dashboard for køen

### Alternative flyter
- **2a.** Ugyldig/utløpt token: Vis "This invite has expired. Ask the queue owner for a new invite."
- **5a.** Bruker klikker "Avslå": Invite oppdateres til DECLINED

### Resultat
Bruker er nå operator for køen, kan serve tickets

---

## UC-INV-03: Tilbakekall Invitasjon

**Aktør:** Owner  
**Forutsetning:** Det finnes en pending invite

### Hovedflyt
1. Owner går til "Team" i dashboard
2. Owner finner pending invite i listen
3. Owner klikker "Cancel invite"
4. System sletter eller markerer invite som REVOKED
5. Inviten forsvinner fra listen

### Resultat
Token er ugyldig, invite kan ikke lenger aksepteres

---

## UC-INV-04: Fjern Team-medlem

**Aktør:** Owner  
**Forutsetning:** Det finnes et team-medlem (operator)

### Hovedflyt
1. Owner går til "Team" i dashboard
2. Owner finner operatoren i medlemslisten
3. Owner klikker "Remove from team"
4. System viser bekreftelsesdialog
5. Owner bekrefter
6. System sletter `QueueMember`-record
7. Operatoren mister tilgang umiddelbart

### Resultat
Operator har ikke lenger tilgang til køen

---

## UC-INV-05: Se Mine Køer (Operator)

**Aktør:** Operator  
**Forutsetning:** Operator er innlogget og medlem av minst én kø

### Hovedflyt
1. Operator går til dashboard
2. System henter alle køer der bruker er medlem
3. Dashboard viser:
   - Køer jeg eier (Owner)
   - Køer jeg jobber på (Operator)
4. Operator kan velge kø og begynne å serve tickets

### Resultat
Operator ser alle køer de har tilgang til

---

## UC-INV-06: Resend Invitasjon

**Aktør:** Owner  
**Forutsetning:** Det finnes en pending invite som er sendt for mer enn X timer siden

### Hovedflyt
1. Owner finner pending invite i listen
2. Owner klikker "Resend invite"
3. System genererer nytt token (gammel ugyldiggjøres)
4. System sender ny e-post
5. `sentAt` og `expiresAt` oppdateres

### Resultat
Ny invite-e-post sendt med ferskt token

---

## Datamodell

### Invite

```kotlin
data class Invite(
    val id: UUID,
    val queueId: UUID,
    val email: String,
    val role: MemberRole,
    val token: String,
    val status: InviteStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val acceptedAt: Instant? = null,
    val acceptedByUserId: String? = null
)

enum class InviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,
    EXPIRED
}

enum class MemberRole {
    OWNER,
    OPERATOR
}
```

### QueueMember

```kotlin
data class QueueMember(
    val id: UUID,
    val queueId: UUID,
    val userId: String,  // Keycloak subject
    val role: MemberRole,
    val joinedAt: Instant,
    val invitedBy: String?  // userId of inviter
)
```

---

## API Endpoints

| Method | Endpoint | Beskrivelse | Auth |
|--------|----------|-------------|------|
| POST | `/api/owner/queues/{id}/invites` | Send invite | Owner |
| GET | `/api/owner/queues/{id}/invites` | List pending invites | Owner |
| DELETE | `/api/owner/queues/{id}/invites/{inviteId}` | Cancel invite | Owner |
| GET | `/api/invites/{token}` | Get invite info (public) | - |
| POST | `/api/invites/{token}/accept` | Accept invite | Authenticated |
| POST | `/api/invites/{token}/decline` | Decline invite | - |
| GET | `/api/owner/queues/{id}/members` | List team members | Owner |
| DELETE | `/api/owner/queues/{id}/members/{memberId}` | Remove member | Owner |
| GET | `/api/me/queues` | Get queues I have access to | Authenticated |

---

## Sikkerhetshensyn

1. **Token-sikkerhet**: Invite tokens må være kryptografisk tilfeldige (UUID v4 eller SecureRandom)
2. **Rate limiting**: Maks 10 invites per time per kø for å hindre spam
3. **E-post validering**: Verifiser e-postformat, men ikke om e-posten eksisterer (GDPR)
4. **Token TTL**: 7 dagers gyldighet, automatisk expire-job
5. **En-gangs-token**: Token kan kun brukes én gang

---

## UI-skisser

### Owner Dashboard - Team Tab
```
┌────────────────────────────────────────┐
│ Team Members                           │
├────────────────────────────────────────┤
│ 👤 Ole Hansen (Owner)          [You]   │
│ 👤 Kari Nordmann (Operator)   [Remove] │
├────────────────────────────────────────┤
│ Pending Invites (1/3 used)             │
├────────────────────────────────────────┤
│ ✉️ test@example.com  Sent 2h ago       │
│    [Resend] [Cancel]                   │
├────────────────────────────────────────┤
│ [+ Invite team member]                 │
│ Upgrade for unlimited invites →        │
└────────────────────────────────────────┘
```

### Invite Accept Page
```
┌────────────────────────────────────────┐
│           You're Invited! 🎉           │
├────────────────────────────────────────┤
│                                        │
│  Ole Hansen invited you to join        │
│  "Bakeri Nordmann" as an Operator      │
│                                        │
│  As an operator you can:               │
│  ✓ Call next customer                  │
│  ✓ Mark tickets as complete            │
│  ✓ View queue statistics               │
│                                        │
│  [Accept Invite]  [Decline]            │
│                                        │
└────────────────────────────────────────┘
```
