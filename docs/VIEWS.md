# Views and Endpoints Documentation

This document lists all web views (HTML pages) and API endpoints, organized by access level.

---

## Public Endpoints (No Authentication Required)

### Web Views

| Endpoint | Template | Description |
|----------|----------|-------------|
| `GET /` | `landing.html` | Landing page (redirects to `/dashboard` or `/portal` if logged in) |
| `GET /signup` | - | Redirects to Keycloak with `prompt=create` for registration |
| `GET /public/q/{queueId}/qr` | `qr-code.html` | Public QR code display page |
| `GET /public/q/{queueId}/join?secret=...` | `join-queue.html` | Join queue page (for STATIC mode) |
| `POST /public/q/{queueId}/ticket` | - | Issue ticket (redirects to ticket status) |
| `GET /public/q/{queueId}/display` | `display-stand.html` | Public display stand (shows queue status) |
| `GET /public/tickets/{ticketId}` | `ticket-status.html` | Ticket status page |
| `POST /public/tickets/{ticketId}/send-email` | - | Send ticket to email (redirects back) |
| `GET /q/{token}` | `join-queue-token.html` | Join queue via dynamic token |
| `POST /q/{token}/ticket` | - | Issue ticket via token (redirects to ticket status) |
| `GET /display/{displayToken}` | `display.html` | Display mode for queue owners |
| `GET /kiosk/{displayToken}` | `kiosk.html` | Kiosk mode with auto-refreshing QR |

### REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/public/queues/{queueId}` | GET | Get queue info |
| `GET /api/public/tickets/{ticketId}` | GET | Get ticket status |
| `POST /api/public/queues/{queueId}/tickets?secret=...` | POST | Issue ticket (legacy) |
| `GET /api/public/tickets/{queueId}/{number}/events` | GET (SSE) | Subscribe to ticket events |
| `GET /api/public/queues/{queueId}/qr-code` | GET | Get current QR code data |
| `GET /api/public/queues/{queueId}/qr-code-fragment` | GET | Get QR code HTML fragment (HTMX) |
| `GET /api/invites/{token}` | GET | Get invite info (for viewing before accepting) |
| `POST /api/invites/{token}/decline` | POST | Decline an invite |
| `POST /api/stripe/webhook` | POST | Stripe webhook (verified by signature) |
| `GET /display/{displayToken}/token/status` | GET | Token status for display pages |

---

## Authenticated Endpoints (Requires Login)

### Web Views

| Endpoint | Template | Description | Access |
|----------|----------|-------------|--------|
| `GET /dashboard` | `dashboard.html` | Queue owner dashboard | Queue owners |
| `GET /portal` | `customer-portal.html` | Customer portal | All authenticated users |
| `GET /portal/history` | `customer-portal-history.html` | Ticket history with pagination | All authenticated users |
| `GET /create-queue` | `create-queue.html` | Create new queue page | All authenticated users |
| `GET /queue/{queueId}/admin` | `admin-queue.html` | Queue admin page | Queue owners/operators |
| `GET /subscription` | `subscription.html` | Subscription management | All authenticated users |
| `GET /subscription/success` | - | Redirect after successful payment | All authenticated users |
| `GET /subscription/cancel` | - | Redirect after cancelled payment | All authenticated users |
| `GET /feedback` | `feedback-form.html` | Submit feedback | All authenticated users |
| `POST /feedback` | - | Submit feedback (redirects) | All authenticated users |
| `GET /feedback/mine` | `my-feedback.html` | View own feedback submissions | All authenticated users |
| `GET /feedback/mine/{id}` | `feedback-detail.html` | View specific feedback item | All authenticated users |
| `GET /debug` | `debug.html` | Debug page | All authenticated users |

### Queue Owner REST API (`/api/owner/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `POST /api/owner/queues` | POST | Create new queue |
| `GET /api/owner/queues/me` | GET | Get my queues |
| `PATCH /api/owner/queues/{id}/status?open=...` | PATCH | Toggle queue open/closed |
| `POST /api/owner/queues/{id}/next` | POST | Call next ticket |
| `POST /api/owner/queues/{id}/tickets/{ticketId}/serve` | POST | Serve specific ticket |
| `POST /api/owner/queues/{id}/tickets/{ticketId}/complete` | POST | Complete ticket |
| `DELETE /api/owner/queues/{id}/tickets/{ticketId}` | DELETE | Revoke/cancel ticket |
| `DELETE /api/owner/queues/{id}` | DELETE | Delete queue |
| `POST /api/owner/queues/{id}/states` | POST | Add queue state |
| `DELETE /api/owner/queues/{id}/states/{stateId}` | DELETE | Remove queue state |
| `PATCH /api/owner/queues/{id}/ticket-page-mode` | PATCH | Update ticket page mode |
| `POST /api/owner/queues/{id}/invites` | POST | Send invite |
| `GET /api/owner/queues/{id}/invites` | GET | Get invites for queue |
| `DELETE /api/owner/queues/{id}/invites/{inviteId}` | DELETE | Revoke invite |
| `GET /api/owner/queues/{id}/members` | GET | Get queue members |
| `DELETE /api/owner/queues/{id}/members/{memberId}` | DELETE | Remove member |
| `GET /api/owner/queues/{id}/token/status` | GET | Get token status for QR refresh |

### Token Management API (`/api/queues/{queueId}/tokens/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/queues/{queueId}/tokens/current` | GET | Get current active token |
| `POST /api/queues/{queueId}/tokens` | POST | Generate new token |
| `DELETE /api/queues/{queueId}/tokens/{tokenId}` | DELETE | Deactivate token |
| `GET /api/queues/{queueId}/tokens` | GET | Get all tokens for queue |
| `PUT /api/queues/{queueId}/tokens/config` | PUT | Update token configuration |

### Invite Acceptance API (`/api/invites/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `POST /api/invites/{token}/accept` | POST | Accept invite (authenticated) |

### Subscription API (`/api/subscription/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/subscription` | GET | Get my subscription |
| `GET /api/subscription/limits` | GET | Get my subscription limits |
| `POST /api/subscription/checkout` | POST | Create Stripe checkout session |
| `POST /api/subscription/portal` | POST | Create Stripe customer portal session |
| `GET /api/subscription/stripe-key` | GET | Get Stripe publishable key |

### Customer Portal API (`/api/me/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/me/portal` | GET | Get portal data as JSON |
| `GET /api/me/active-ticket` | GET | Get active ticket |
| `GET /api/me/ticket-history` | GET | Get ticket history with pagination |

### User Preferences API (`/api/preferences/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/preferences` | GET | Get user preferences |
| `POST /api/preferences/language` | POST | Set language preference |

---

## Seller Endpoints (Requires SELLER Role)

### Web Views

| Endpoint | Template | Description |
|----------|----------|-------------|
| `GET /seller/dashboard` | `seller-dashboard.html` | Seller dashboard |
| `GET /seller/connect/return` | `seller-connect-return.html` | Stripe Connect return page |
| `GET /seller/connect/refresh` | `seller-connect-refresh.html` | Stripe Connect refresh page |

### Seller API (`/api/seller/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/seller/me` | GET | Get seller profile |
| `GET /api/seller/dashboard` | GET | Get seller dashboard data |
| `GET /api/seller/organizations` | GET | Get organizations created by seller |
| `POST /api/seller/organizations` | POST | Create new organization |
| `GET /api/seller/referral-link` | GET | Get referral link |

### Seller Connect API (`/api/seller/connect/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `POST /api/seller/connect/start` | POST | Start Stripe Connect onboarding |
| `GET /api/seller/connect/status` | GET | Get Stripe Connect status |
| `POST /api/seller/connect/dashboard` | POST | Get Stripe dashboard login link |
| `POST /api/seller/connect/refresh-link` | POST | Get new onboarding link |

---

## Superadmin Endpoints (Requires SUPERADMIN Role)

### Web Views

| Endpoint | Template | Description |
|----------|----------|-------------|
| `GET /admin/sales` | `admin-sales.html` | Sales admin dashboard |
| `GET /admin/sales/sellers/new` | `admin-sales-seller-new.html` | Create new seller form |
| `POST /admin/sales/sellers` | - | Create seller (redirects) |
| `GET /admin/tier-limits` | `admin-tier-limits.html` | Tier limits management |
| `GET /admin/feedback` | `admin-feedback.html` | View all feedback |
| `GET /admin/feedback/{id}` | `admin-feedback-detail.html` | View feedback detail |
| `POST /admin/feedback/{id}/status` | - | Update feedback status |
| `POST /admin/feedback/{id}/notes` | - | Add note to feedback |

### Sales Admin API (`/api/admin/sales/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/admin/sales/dashboard` | GET | Get admin sales dashboard |
| `GET /api/admin/sales/sellers` | GET | List all sellers |
| `GET /api/admin/sales/sellers/{id}` | GET | Get specific seller |
| `POST /api/admin/sales/sellers` | POST | Create seller |
| `PUT /api/admin/sales/sellers/{id}` | PUT | Update seller |
| `DELETE /api/admin/sales/sellers/{id}` | DELETE | Deactivate seller |
| `GET /api/admin/sales/users/search?query=...` | GET | Search users for manual referral |
| `POST /api/admin/sales/referrals/manual` | POST | Create manual referral |

### Tier Limits API (`/api/admin/tier-limits/*`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/admin/tier-limits` | GET | Get all tier limits |
| `GET /api/admin/tier-limits/{tier}` | GET | Get tier limit by name |
| `PUT /api/admin/tier-limits/{tier}` | PUT | Update tier limit |

---

## Navigation Flow

### For Queue Owners (Dashboard Flow)
```
/ → /dashboard
    ├── /queue/{id}/admin → Queue administration
    │       ├── Member management
    │       └── State management
    ├── /create-queue → Create new queue
    ├── /subscription → Manage subscription
    ├── /feedback → Submit feedback
    └── /feedback/mine → View my feedback
```

### For Customers (Portal Flow)
```
/ → /portal (if no queues)
    ├── Active ticket link → /public/tickets/{id}
    ├── /portal/history → Full ticket history
    └── /feedback → Submit feedback
```

### For Public Queue Display
```
/display/{displayToken} → Queue display mode
/kiosk/{displayToken} → Kiosk mode with QR
/public/q/{queueId}/display → Display stand (queue status)
```

### For Joining Queue
```
Scan QR Code
    ├── Static mode: /public/q/{queueId}/join?secret=...
    └── Dynamic mode: /q/{token}
        ↓
/public/tickets/{ticketId} → Ticket status page
```

---

## Access Token Modes

| Mode | Description | Join URL Pattern |
|------|-------------|-----------------|
| `STATIC` | Legacy mode, uses `qr_code_secret` | `/public/q/{queueId}/join?secret={secret}` |
| `ROTATING` | Tokens rotate on schedule | `/q/{token}` |
| `ONE_TIME` | Each token used once | `/q/{token}` |
| `TIME_LIMITED` | Tokens expire after time | `/q/{token}` |

**Note:** When `accessTokenMode != STATIC`, the legacy `/public/q/{queueId}/join?secret=...` endpoint will reject ticket creation with an error message directing users to use the `/q/{token}` endpoint instead.

---

## Templates Reference

| Template | Used By | Purpose |
|----------|---------|---------|
| `landing.html` | `/` | Public landing page |
| `dashboard.html` | `/dashboard` | Queue owner dashboard |
| `customer-portal.html` | `/portal` | Customer portal |
| `customer-portal-history.html` | `/portal/history` | Ticket history |
| `create-queue.html` | `/create-queue` | Create queue form |
| `admin-queue.html` | `/queue/{id}/admin` | Queue admin |
| `join-queue.html` | `/public/q/{id}/join` | Join queue (static) |
| `join-queue-token.html` | `/q/{token}` | Join queue (dynamic) |
| `ticket-status.html` | `/public/tickets/{id}` | Ticket status |
| `qr-code.html` | `/public/q/{id}/qr` | QR code display |
| `display.html` | `/display/{token}` | Display mode |
| `display-stand.html` | `/public/q/{id}/display` | Display stand |
| `kiosk.html` | `/kiosk/{token}` | Kiosk mode |
| `subscription.html` | `/subscription` | Subscription page |
| `feedback-form.html` | `/feedback` | Feedback form |
| `my-feedback.html` | `/feedback/mine` | My feedback |
| `feedback-detail.html` | `/feedback/mine/{id}` | Feedback detail |
| `seller-dashboard.html` | `/seller/dashboard` | Seller dashboard |
| `seller-connect-return.html` | `/seller/connect/return` | Stripe return |
| `seller-connect-refresh.html` | `/seller/connect/refresh` | Stripe refresh |
| `admin-sales.html` | `/admin/sales` | Sales admin |
| `admin-sales-seller-new.html` | `/admin/sales/sellers/new` | New seller |
| `admin-tier-limits.html` | `/admin/tier-limits` | Tier limits |
| `admin-feedback.html` | `/admin/feedback` | Admin feedback list |
| `admin-feedback-detail.html` | `/admin/feedback/{id}` | Admin feedback detail |
| `token-error.html` | Token errors | Invalid token error |
| `queue-closed.html` | Queue closed | Queue closed message |
| `error.html` | Various | Generic error |
| `debug.html` | `/debug` | Debug page |
