# Design: Extended Organization & Queue Data Model

## Background

SimpleQueue needs richer data for organizations and queues to support:
1. **Service discovery** - Users searching for services by type, location
2. **Business profiles** - Companies showing relevant info on public pages
3. **Visibility control** - Choose what info appears on QR/public pages

## Current State Analysis

### Organization (current)
```kotlin
Organization(
    id, name, orgNumber,
    createdBySellerId, adminEmail, adminUserId,
    subscriptionId, status, createdAt, activatedAt
)
```
Very minimal - only tracks admin and sales relationship.

### Queue (current)
```kotlin
Queue(
    id, name, ownerId, open,
    qrCodeSecret, qrCodeType, lastRotatedAt, ticketPageMode
)
```
Pure queue mechanics - no business context.

### Relationships
- `Queue.organization_id` → links queue to organization (added in V8)
- `Seller` → creates `Organization` → admin signs up
- `SellerReferral` → tracks commission on org or individual user

## Proposed Changes

### 1. Organization - Extended Fields

**Business Info:**
| Field | Type | Notes |
|-------|------|-------|
| `description` | TEXT | Business description |
| `website` | VARCHAR(500) | Company website |
| `email` | VARCHAR(255) | Public contact email |
| `phone` | VARCHAR(50) | Public phone number |
| `logo_url` | VARCHAR(500) | Logo image URL |

**Location:**
| Field | Type | Notes |
|-------|------|-------|
| `street_address` | VARCHAR(255) | Street name + number |
| `postal_code` | VARCHAR(20) | Postnummer |
| `city` | VARCHAR(100) | Poststed |
| `country` | VARCHAR(100) | Default: Norge |
| `latitude` | DECIMAL(10,7) | For map search |
| `longitude` | DECIMAL(10,7) | For map search |

**Visibility Flags:**
| Field | Type | Notes |
|-------|------|-------|
| `show_description` | BOOLEAN | Show on public pages |
| `show_address` | BOOLEAN | Show full address |
| `show_phone` | BOOLEAN | Show phone number |
| `show_email` | BOOLEAN | Show email |
| `show_website` | BOOLEAN | Show website link |
| `show_hours` | BOOLEAN | Show opening hours |
| `public_listed` | BOOLEAN | Appear in service search |

### 2. Queue - Extended Fields

**Queue-specific info (not duplicating org):**
| Field | Type | Notes |
|-------|------|-------|
| `description` | TEXT | Queue-specific description |
| `location_hint` | VARCHAR(255) | "2nd floor", "Room A" |
| `estimated_service_time` | INTEGER | Minutes per customer (hint) |

Most business info lives on Organization. Queue just describes the specific service point.

### 3. Opening Hours

**New table: `opening_hours`**
```sql
CREATE TABLE opening_hours (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    day_of_week INTEGER NOT NULL,  -- 1=Monday, 7=Sunday (ISO)
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    is_closed BOOLEAN DEFAULT FALSE,
    UNIQUE(organization_id, day_of_week)
);
```

Allows:
- Regular hours per day of week
- Marking specific days as closed
- Future: Could add `special_dates` table for holidays

### 4. Service Categories (Tags)

**New table: `categories`**
```sql
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,  -- 'healthcare', 'government', etc.
    name_no VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    icon VARCHAR(50),  -- Optional icon class/name
    parent_id UUID REFERENCES categories(id),  -- For hierarchy
    sort_order INTEGER DEFAULT 0
);
```

**New table: `organization_categories`**
```sql
CREATE TABLE organization_categories (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    PRIMARY KEY (organization_id, category_id)
);
```

**Suggested initial categories:**
- Healthcare (Helse)
  - Doctor (Lege)
  - Dentist (Tannlege)
  - Pharmacy (Apotek)
- Government (Offentlig)
  - NAV
  - Police (Politi)
  - Tax (Skatt)
- Services (Tjenester)
  - Bank
  - Post Office (Post)
  - DMV (Trafikkstasjon)
- Retail (Butikk)
  - Electronics
  - Service counter

### 5. Domain Model Updates

```kotlin
// Extended Organization
data class Organization(
    // ... existing fields ...
    
    // Business info
    val description: String?,
    val website: String?,
    val contactEmail: String?,  // Different from adminEmail
    val phone: String?,
    val logoUrl: String?,
    
    // Location
    val location: Location?,
    
    // Visibility
    val visibility: OrganizationVisibility,
    
    // Opening hours (loaded separately)
)

data class Location(
    val streetAddress: String?,
    val postalCode: String?,
    val city: String?,
    val country: String = "Norge",
    val latitude: Double?,
    val longitude: Double?,
)

data class OrganizationVisibility(
    val showDescription: Boolean = true,
    val showAddress: Boolean = true,
    val showPhone: Boolean = true,
    val showEmail: Boolean = true,
    val showWebsite: Boolean = true,
    val showHours: Boolean = true,
    val publicListed: Boolean = false,  // Opt-in for discovery
)

data class OpeningHours(
    val id: UUID,
    val organizationId: UUID,
    val dayOfWeek: DayOfWeek,  // java.time.DayOfWeek
    val opensAt: LocalTime,
    val closesAt: LocalTime,
    val isClosed: Boolean = false,
)

data class Category(
    val id: UUID,
    val slug: String,
    val nameNo: String,
    val nameEn: String,
    val icon: String?,
    val parentId: UUID?,
    val sortOrder: Int,
)
```

### 6. Queue Model Updates

```kotlin
data class Queue(
    // ... existing fields ...
    val description: String?,
    val locationHint: String?,
    val estimatedServiceTimeMinutes: Int?,
)
```

## Implementation Plan

### Phase 1: Database Migration
1. `V9__extend_organization_fields.sql` - Add columns to organizations
2. `V10__add_opening_hours.sql` - Create opening_hours table  
3. `V11__add_categories.sql` - Create categories tables + seed data
4. `V12__extend_queue_fields.sql` - Add queue description fields

### Phase 2: Domain & Repository
1. Update `Organization` model with new fields
2. Add `Location`, `OrganizationVisibility`, `OpeningHours`, `Category` models
3. Update `OrganizationRepository` port
4. Add new ports: `OpeningHoursRepository`, `CategoryRepository`

### Phase 3: Application Layer
1. Update `CreateOrganizationUseCase` with new fields
2. Add `UpdateOrganizationUseCase`
3. Add `ManageOpeningHoursUseCase`
4. Add search/discovery use cases (future)

### Phase 4: Infrastructure
1. Implement repository adapters
2. Update controllers for org editing
3. Update Thymeleaf templates for forms
4. Add public organization profile page

### Phase 5: Public Pages
1. Show org info on QR/ticket pages (respecting visibility)
2. Organization profile page `/public/org/{id}`
3. (Future) Service directory with search/filter

## Open Questions

1. **Geo-coding**: Auto-fetch lat/lng from address? (External API needed)
2. **Logo storage**: URL only, or file upload? (S3/MinIO?)
3. **Multi-language descriptions**: Just Norwegian, or i18n support?
4. **Queue-specific hours**: Should queues have their own hours, or always use org?

## Migration Strategy

All new fields are nullable/have defaults - fully backward compatible. Existing organizations continue working; new fields are just empty until edited.
