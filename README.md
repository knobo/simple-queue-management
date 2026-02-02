# Simple Queue Service

A hexagonal architecture, mobile-first queue management system.

## Features
- **Queue Owners**: Register, Open/Close queues, Call next customer.
- **Public Users**: Scan QR code, get a ticket, see live wait times.
- **Push Notifications**: Integration with `ntfy.sh`.
- **Tech Stack**: Spring Boot 4, Java 21, PostgreSQL, Flyway, React, Material UI.

## How to Run

### 1. Start Infrastructure
Ensure Docker is running, then start the dependencies:
```bash
docker-compose up -d
```
This starts PostgreSQL, Keycloak, and a local ntfy server.

### 2. Start Backend

## Configuration

The application requires the following environment variables. You can set them in a `.env` file in the project root.

| Variable | Description | Required | Reference |
|----------|-------------|----------|-----------|
| `KEYCLOAK_REALM` | Keycloak Realm name | Yes | `simple-queue` |
| `KEYCLOAK_AUTH_SERVER_URL` | Keycloak Server URL | Yes | `https://login.knobo.no` |
| `KEYCLOAK_CLIENT_ID` | Keycloak Client ID | Yes | `web` |
| `KEYCLOAK_CLIENT_SECRET` | Keycloak Client Secret | Yes | |
| `NTFY_USERNAME` | Username for ntfy.sh (if protected) | No | `simple-queue` |
| `NTFY_PASSWORD` | Password for ntfy.sh | No | |

### 3. Run with Gradle
```bash
./gradlew :infrastructure:bootRun
```

### 4. Start Frontend
```bash
cd frontend
npm install
npm run dev
```

### 5. Keycloak Setup (MVP)
- Access Keycloak at `http://localhost:8080` (admin/admin).
- Create a realm (e.g., `master` or a new one).
- Configure the `spring.security.oauth2.resourceserver.jwt.issuer-uri` in `application.properties` to match your realm.

## Round Trip Workflow
1. Register a queue at `/register`.
2. Go to the dashboard and open the queue.
3. Click "Show QR Code" to see the printable join page.
4. Scan (or open the link) to get a ticket.
5. Watch the status page update in real-time as the owner clicks "Call Next".
