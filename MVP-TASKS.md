# Simple Queue — MVP Production Tasks

## Status
Last updated: 2026-02-01

## 🔴 Priority 1: QR Code Security & Tokens

### Task 1.1: One-Time QR Codes (UNIQUE)
**When `accessTokenMode = ONE_TIME`:**
- [ ] Generate new token after each successful ticket issue
- [ ] Invalidate token immediately after use (`use_count >= max_uses`)
- [ ] Return error if token already used
- [ ] Integration test: Same QR code cannot issue two tickets

**Files to modify:**
- `IssueTicketUseCase.kt` — check token validity, record use
- `QueueAccessTokenRepository` — update use count
- `IssueTicketIntegrationTest.kt` — add one-time token tests

### Task 1.2: Rotating QR Codes
**When `accessTokenMode = ROTATING`:**
- [ ] Auto-rotate token after `tokenRotationMinutes`
- [ ] Old tokens invalid after rotation
- [ ] Endpoint to check token validity + time remaining
- [ ] Frontend: Auto-reload QR when expired (WebSocket or polling)

**Files to modify:**
- `QueueAccessTokenService.kt` — rotation logic
- `qr-code.html` — add auto-refresh JS
- Add REST endpoint: `GET /api/queue/{id}/token/status`

### Task 1.3: QR Code Page Auto-Refresh
- [ ] When new token generated → page reloads automatically
- [ ] For rotating: countdown timer showing validity
- [ ] WebSocket or SSE for real-time updates
- [ ] Fallback: polling every 30 seconds

---

## 🔴 Priority 2: Kiosk Mode

### Task 2.1: Kiosk Mode Detection
- [ ] URL parameter: `?kiosk=true` or `?mode=kiosk`
- [ ] Store kiosk state in sessionStorage (not localStorage)
- [ ] Display "Kiosk Mode Active" indicator
- [ ] In kiosk mode: **DO NOT** save tickets to localStorage

**Files to modify:**
- `join-queue.html` / `ticket-status.html` — detect kiosk param
- Remove localStorage writes when kiosk=true

### Task 2.2: Kiosk Configuration Link Generator
- [ ] Admin panel: Generate kiosk display URL
- [ ] URL includes: queue ID, display token, kiosk=true
- [ ] Copy-to-clipboard button
- [ ] Optional: QR code of the kiosk URL itself

**Files to modify:**
- `admin-queue.html` — add kiosk link generator section

---

## 🔴 Priority 3: Multi-Ticket LocalStorage

### Task 3.1: Store Multiple Tickets
**Current:** Single ticket object in localStorage
**Target:** Array of ticket objects

- [ ] Change localStorage schema: `tickets: [{...}, {...}]`
- [ ] Migration: Convert old single-object to array on page load
- [ ] Each ticket has: `queueId`, `ticketId`, `number`, `createdAt`, `queueName`
- [ ] Dedup by ticketId

**Files to modify:**
- JS in `home.html`, `join-queue.html`, `ticket-status.html`

### Task 3.2: Improved Ticket Display on Homepage
- [ ] List all tickets from localStorage
- [ ] Show: Queue name, ticket number, status (if available)
- [ ] "View" button → goes to ticket status page
- [ ] "Remove" button with confirm dialog
- [ ] Sort by createdAt (newest first)

**Files to modify:**
- `home.html` — add ticket list section

---

## 🔴 Priority 4: UX Fixes

### Task 4.0: Remove Confirm Popup on "Call Next"
- [ ] Dashboard "Call next" button should act immediately
- [ ] No confirm dialog — just call the next customer
- [ ] Optional: Brief toast notification "Called #X"

**Files to modify:**
- `infrastructure/src/main/resources/templates/dashboard.html`

---

## 🟡 Priority 5: Integration Tests

### Task 4.1: QR Code Security Tests
```kotlin
// One-time token tests
@Test fun oneTimeToken_cannotBeUsedTwice()
@Test fun oneTimeToken_newTokenGeneratedAfterUse()

// Rotating token tests  
@Test fun rotatingToken_validWithinTimeWindow()
@Test fun rotatingToken_invalidAfterExpiry()
@Test fun rotatingToken_oldTokenInvalidAfterRotation()

// Time-limited token tests
@Test fun timeLimitedToken_validBeforeExpiry()
@Test fun timeLimitedToken_invalidAfterExpiry()
```

### Task 4.2: Kiosk Mode Tests
```kotlin
@Test fun kioskMode_ticketNotStoredInLocalStorage()
@Test fun kioskMode_displayIndicatorShown()
```

### Task 4.3: Multi-Ticket Tests
```kotlin
@Test fun multipleTickets_allStoredInLocalStorage()
@Test fun multipleTickets_displayedOnHomepage()
@Test fun multipleTickets_canBeRemovedIndividually()
```

---

## Database Schema (if needed)

Already have `queue_access_tokens` table with:
- `id`, `queue_id`, `token`, `expires_at`, `max_uses`, `use_count`, `is_active`, `created_at`

May need index on `(queue_id, is_active, expires_at)` for efficient lookups.

---

## Notes

- AccessTokenMode enum already exists: STATIC, ROTATING, ONE_TIME, TIME_LIMITED
- QueueAccessToken model already has validation logic
- Need to wire up the token validation in IssueTicketUseCase
