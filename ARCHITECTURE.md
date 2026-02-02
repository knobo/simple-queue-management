# Simple Queue — Architecture

*Oppdatert: 2026-02-01*

---

## Systemarkitektur

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Kubernetes Cluster (k3s)                         │
│                                                                              │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐         │
│  │   Traefik       │    │   Keycloak      │    │   PostgreSQL    │         │
│  │   (Ingress)     │    │   login.knobo.no│    │   (queue ns)    │         │
│  │   traefik ns    │    │   (external)    │    │                 │         │
│  └────────┬────────┘    └────────┬────────┘    └────────┬────────┘         │
│           │                      │                      │                   │
│           ▼                      ▼                      ▼                   │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                        Simple Queue                                 │    │
│  │                     queue.knobo.no                                  │    │
│  │                     (queue namespace)                               │    │
│  │                                                                     │    │
│  │  ┌───────────────────────────────────────────────────────────────┐ │    │
│  │  │                    Spring Boot 4 Application                   │ │    │
│  │  │                                                                │ │    │
│  │  │  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────┐  │ │    │
│  │  │  │   Controllers   │  │    Services     │  │  Repositories │  │ │    │
│  │  │  │   (REST API)    │  │   (Use Cases)   │  │  (JDBC)       │  │ │    │
│  │  │  └─────────────────┘  └─────────────────┘  └───────────────┘  │ │    │
│  │  │                                                                │ │    │
│  │  │  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────┐  │ │    │
│  │  │  │   Thymeleaf     │  │   WebSocket     │  │   SSE         │  │ │    │
│  │  │  │   (Templates)   │  │   (Real-time)   │  │  (Events)     │  │ │    │
│  │  │  └─────────────────┘  └─────────────────┘  └───────────────┘  │ │    │
│  │  └───────────────────────────────────────────────────────────────┘ │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐         │
│  │   ntfy          │    │   Stripe        │    │   SMTP          │         │
│  │   (Push notif)  │    │   (Payments)    │    │   10.0.0.1:25   │         │
│  │   ntfy.knobo.no │    │   (external)    │    │   (internal)    │         │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Keycloak (Autentisering)

### Konfigurasjon

| Setting | Verdi |
|---------|-------|
| **URL** | https://login.knobo.no |
| **Realm** | `simple-queue` |
| **Client ID** | `web` |
| **Client Secret** | Se `queue-secrets` i K8s |
| **Admin Console** | https://login.knobo.no/admin |
| **Admin User** | `admin` |
| **Admin Password** | Se `pass keycloak/enjord/admin` |

### Roller

| Rolle | Beskrivelse | Tilgang |
|-------|-------------|---------|
| `superadmin` | Full admin | `/admin/*`, tier limits, feedback admin |
| `seller` | Selger/partner | `/seller/*`, referral dashboard |
| `queue-owner` | Kø-eier | Opprette/administrere køer |
| (ingen) | Vanlig bruker | Ta billett, se status |

### Test-brukere

| Bruker | ID | Rolle |
|--------|-----|-------|
| `bohmer@gmail.com` | a53ef32d-... | superadmin |
| `owner-cafe@test.queue.knobo.no` | 74abf2f8-... | queue-owner |
| `owner-clinic@test.queue.knobo.no` | 9c42caff-... | queue-owner |
| `owner-salon@test.queue.knobo.no` | cefbb850-... | queue-owner |
| `seller-anna@test.queue.knobo.no` | 4272d77a-... | seller |
| `seller-bob@test.queue.knobo.no` | d34f4e00-... | seller |
| `customer-per@test.queue.knobo.no` | d011c1a3-... | (ingen) |
| `customer-ole@test.queue.knobo.no` | 69e1078d-... | (ingen) |
| `customer-kari@test.queue.knobo.no` | fd5301b2-... | (ingen) |

**Test-passord:** Bruk "Forgot password" eller reset via admin console.

---

## Kubernetes (queue namespace)

### Secrets

```bash
# Se secrets
KUBECONFIG=~/.kube/config kubectl get secret queue-secrets -n queue -o yaml

# Secrets inneholder:
# - DB_PASSWORD
# - KEYCLOAK_CLIENT_SECRET
# - NTFY_PASSWORD
# - STRIPE_SECRET_KEY
# - STRIPE_WEBHOOK_SECRET
```

### Deployment Environment

| Variabel | Verdi |
|----------|-------|
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://postgres:5432/queue |
| `KEYCLOAK_REALM` | simple-queue |
| `KEYCLOAK_AUTH_SERVER_URL` | https://login.knobo.no |
| `KEYCLOAK_CLIENT_ID` | web |
| `NTFY_URL` | https://ntfy.knobo.no |
| `APP_BASE_URL` | https://queue.knobo.no |
| `SMTP_HOST` | 10.0.0.1 |
| `SMTP_PORT` | 25 |

### Nyttige kommandoer

```bash
# Sjekk pods
KUBECONFIG=~/.kube/config kubectl get pods -n queue

# Se logs
KUBECONFIG=~/.kube/config kubectl logs -f deployment/simple-queue -n queue

# Restart deployment
KUBECONFIG=~/.kube/config kubectl rollout restart deployment/simple-queue -n queue

# Port-forward for lokal debugging
KUBECONFIG=~/.kube/config kubectl port-forward svc/simple-queue 8080:8080 -n queue
```

---

## Staging & PR Testing

Vi har et eget staging-miljø (`simple-queue-staging`) som støtter dynamisk opprettelse av isolerte miljøer per Pull Request (PR).

### Oversikt
Hver PR får:
1. En egen **database** (`queue_pr_<N>`) isolert fra andre.
2. En unik **URL** (`https://pr-<N>.queue.knobo.no`).
3. En egen **deployment** i `simple-queue-staging` navnerommet.

### Workflow

1. **Deploy:** Kjøres manuelt (eller via CI i fremtiden).
   ```bash
   ./scripts/deploy-pr.sh <PR_NUMBER>
   ```
   Dette bygger docker-image, oppretter DB, og deployer til K8s.

2. **Test:** Åpne URL-en (`https://pr-<N>.queue.knobo.no`) og test endringene.

3. **Cleanup:** Når PR-en er merget eller lukket.
   ```bash
   ./scripts/deploy-pr.sh <PR_NUMBER> --delete
   ```
   Dette sletter deployment, service, ingress og dropper databasen.

### Infrastruktur

| Komponent | Beskrivelse |
|-----------|-------------|
| **Namespace** | `simple-queue-staging` |
| **Database** | Felles Postgres-instans i staging, men unik database (`queue_pr_<N>`) per PR. |
| **Secrets** | Kopiert fra `queue` namespace (samme nøkler). |
| **Ingress** | Traefik ruter `pr-*.queue.knobo.no` til riktig service. |
| **Image** | Bygges lokalt/i CI og pushes som `simple-queue:pr-<N>`. |

---

## Hexagonal Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │   Controllers    Repositories    Config    Adapters      │   │
│  │   (REST/Web)     (JDBC)          (Spring)  (Keycloak)   │   │
│  │                                                          │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │                      APPLICATION                          │   │
│  │                                                           │   │
│  │   Use Cases          Services          DTOs               │   │
│  │   (IssueTicket)      (Subscription)    (Request/Response)│   │
│  │                                                           │   │
│  └──────────────────────────┬────────────────────────────────┘   │
│                             │                                    │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │                        DOMAIN                             │   │
│  │                                                           │   │
│  │   Entities           Ports              Value Objects     │   │
│  │   (Queue, Ticket)    (Repositories)     (TicketCode)     │   │
│  │                                                           │   │
│  └───────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

Avhengigheter går INNOVER: Infrastructure → Application → Domain
```

---

## Database Schema (PostgreSQL)

### Hovedtabeller

| Tabell | Beskrivelse |
|--------|-------------|
| `queues` | Køer med settings |
| `tickets` | Billetter i køer |
| `queue_states` | Custom states (WAITING, SERVING, etc.) |
| `queue_members` | Operatører koblet til køer |
| `invites` | Invitasjoner til operatører |
| `subscriptions` | Bruker-abonnement (FREE, STARTER, PRO) |
| `tier_limits` | Konfigurerbare tier-begrensninger |
| `queue_access_tokens` | QR-kode tokens |
| `sellers` | Selger/partner-info |
| `feedback` | Bruker-feedback |

### Migrasjoner

Flyway-migrasjoner i:
```
infrastructure/src/main/resources/db/migration/V*.sql
```

---

## URLs

| Tjeneste | URL |
|----------|-----|
| **App (Prod)** | https://queue.knobo.no |
| **App (PR-Test)** | `https://pr-<N>.queue.knobo.no` |
| **Keycloak** | https://login.knobo.no |
| **Keycloak Admin** | https://login.knobo.no/admin |
| **ntfy** | https://ntfy.knobo.no |
| **GitHub Repo** | https://github.com/knobo/simple-queue |

---

## Lokal Utvikling

### Forutsetninger

- Java 21 (se AGENTS.md)
- Docker (for Testcontainers)
- PostgreSQL (via docker-compose eller Testcontainers)

### Starte lokalt

```bash
# Start PostgreSQL
docker-compose up -d postgres

# Kjør med lokale env-variabler
export KEYCLOAK_AUTH_SERVER_URL=https://login.knobo.no
export KEYCLOAK_REALM=simple-queue
export KEYCLOAK_CLIENT_ID=web
export KEYCLOAK_CLIENT_SECRET=z4E6D1UkmY1vZGq4OAo8XWl7aIZs1jn0

JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :infrastructure:bootRun
```

---

*Spørsmål? Ping Astra 🛡️*
