# Simple Queue — Product Roadmap

*Sist oppdatert: 2026-02-01*

## Visjon

Et enkelt, mobilvennlig køsystem for små bedrifter. Kunder skanner QR-kode, får billett, ser sanntidsstatus. Kø-eiere kaller neste kunde med ett klikk.

---

## 🚀 MVP (Nåværende Sprint)

### Sikkerhet & QR-koder
- [ ] **One-time QR codes** — Hver kode kan kun brukes én gang
- [ ] **Rotating QR codes** — Koder roterer automatisk etter X minutter
- [ ] **Auto-refresh QR page** — Siden oppdateres når token utløper
- [ ] Database-backed tokens med `queue_access_tokens` tabell

### Kiosk Mode
- [ ] **Kiosk-modus via URL** — `?kiosk=true` parameter
- [ ] **Ingen localStorage i kiosk** — Tickets lagres ikke permanent
- [ ] **Kiosk URL generator** — Admin kan generere lenke til display

### Multi-Ticket Support
- [ ] **Array i localStorage** — Støtte for flere billetter samtidig
- [ ] **Forbedret forside** — Vis alle aktive billetter
- [ ] **Fjern-knapp** — Slett individuelle billetter med bekreftelse

### UX Forbedringer
- [ ] **Fjern confirm på "Call next"** — Direkte handling uten popup
- [ ] **Bedre feilmeldinger** — Tydelige beskjeder ved ugyldig QR

### Testing
- [ ] Integrasjonstester for token-validering
- [ ] E2E tester for kiosk mode
- [ ] E2E tester for multi-ticket flows

---

## 🟡 Fase 2: Betalingsintegrasjon

### Stripe Connect
- [x] Onboarding flow for selgere
- [ ] Subscription checkout
- [ ] Customer portal for administrasjon
- [ ] Webhook-håndtering

### Pricing Tiers
- [x] FREE / STARTER / PRO / ENTERPRISE
- [x] Tier limits enforcement
- [ ] Upgrade/downgrade flows

---

## 🟢 Fase 3: Avanserte Features

### Analytics
- [ ] Ventetids-statistikk
- [ ] Gjennomstrømningstall
- [ ] Peak hours analyse

### Notifications
- [ ] Push-varsler når det er din tur
- [ ] SMS-varsling (valgfritt)
- [ ] Email-varsling

### Multi-location
- [ ] Organisasjoner med flere køer
- [ ] Sentralt dashboard
- [ ] Operator-roller per lokasjon

---

## Tech Stack

- **Backend:** Spring Boot 4, Kotlin, PostgreSQL
- **Frontend:** Thymeleaf templates, vanilla JS
- **Auth:** Keycloak
- **Payments:** Stripe Connect
- **Infra:** Docker, GitHub Actions CI/CD

---

## Development Workflow

1. **Agenter:** Bruk Kimi K2 for kodeoppgaver via OpenClaw sub-agents
2. **Git:** Feature branches → PR med automerge label
3. **Testing:** `./gradlew check` må være grønn før merge
4. **Deploy:** Auto-deploy til staging ved merge til main

---

## Kontakt

- **Prosjekteier:** Knobo
- **AI Lead:** Astra 🛡️
