# Passlify Event Domain — Functional and Technical Specification

> **Document type:** Product requirement, domain model, API contract, implementation guide, and AI coding specification  
> **Scope:** Event creation, configuration, ownership, collaboration, publication, lifecycle, location, contacts, event classification, commercial configuration, audit, and related validation  
> **Out of scope:** Ticket-type rules, checkout, payment-session implementation, coupons, seating, attendee registration, ticket issuance, scanning details, refunds, and payouts, except where the Event domain must expose configuration needed by those modules  
> **Target stack:** Java, Spring Boot, Spring Security, JPA/Hibernate, PostgreSQL, Flyway, Keycloak, RFC 7807 error responses

---

# 1. Purpose

The Event domain is the central aggregate of Passlify.

An organizer creates an event first, configures its business and operational details, invites additional collaborators, chooses whether it is public or private, defines whether it is free or paid, assigns a location or online destination, and publishes the event when all mandatory requirements are satisfied.

Every ticket type, attendee form, order, ticket, payment, scan, report, and notification belongs to an event. The event therefore acts as the primary business boundary for organizer authorization, financial configuration, inventory ownership, reporting, and audit.

The design must support both:

- a simple individual creating a free event; and
- a registered company organizing a paid commercial event.

The organizer may create any number of events. Passlify does not impose an application-level event-count limit in the initial implementation.

---

# 2. Primary actors

## 2.1 Organizer

A Keycloak user with the `ORGANIZER` role.

An organizer may:

- create events;
- view events they own or collaborate on;
- edit events according to their event-level permissions;
- invite collaborators;
- configure tickets and event forms;
- publish, unpublish, cancel, or complete events when authorized;
- view event reports;
- manage event operations.

The organizer who creates the event becomes its original owner.

## 2.2 Event collaborator

A user invited to one specific event.

A collaborator does not automatically own the event and does not gain access to other events belonging to the owner or organization.

Collaborator permissions are event-scoped.

Suggested event roles:

- `OWNER`
- `MANAGER`
- `EDITOR`
- `VIEWER`
- `CHECK_IN_OPERATOR`

These roles may be implemented in phases, but the domain model must not assume that only one user can administer an event.

## 2.3 Platform administrator

A Keycloak user with the `ADMIN` role.

An administrator may:

- view and manage every event;
- override event access when required for support or compliance;
- assign or approve payment-provider availability;
- configure allowed currencies per payment provider;
- block or disable an event;
- inspect complete audit history;
- manage event-type reference data.

## 2.4 Buyer or attendee

A public or authenticated user who can view a published public event and later register or purchase tickets.

This actor does not manage the Event aggregate.

---

# 3. Core business principles

1. An organizer can create an unlimited number of events.
2. A newly created event starts as `DRAFT`.
3. A newly created event starts as `PRIVATE`.
4. A draft event is not available in the public event catalogue.
5. A public event becomes publicly visible only after it is successfully published.
6. A private event may still be published, but it is accessible only through its private link or another controlled access mechanism.
7. Free events are allowed for both individuals and companies.
8. Paid events require a complete company organization profile.
9. The organizer cannot freely choose any payment provider.
10. Available payment providers are assigned or approved by a platform administrator.
11. Currency options depend on the selected payment provider.
12. Every material event change must be audited.
13. Event ownership and collaborator access are event-scoped.
14. The public URL uses a human-readable unique slug.
15. The internal event identifier must not depend on the slug.
16. The event must also have a non-sequential public identifier suitable for URLs, QR payloads, integrations, logs, and support.
17. Event description must support formatted rich text while remaining safe for public rendering.
18. Event location supports in-person, online, and hybrid events.
19. Event properties such as age restrictions and multiple entry must be explicit structured data, not embedded only in free-text descriptions.
20. Event contact and social data belong to the event and may differ from the organizer organization profile.

---

# 4. Event aggregate

The `Event` entity is the aggregate root.

Recommended aggregate-owned or closely associated objects:

- `Event`
- `EventLocation`
- `EventContact`
- `EventSettings`
- `EventCommercialSettings`
- `EventCollaborator`
- `EventAuditEntry`
- `EventType`
- optional `EventTag`

An event must not be implemented as a single unbounded entity containing every future field. Stable concepts should be separated into focused embeddables or one-to-one entities.

---

# 5. Event identity

Each event must have three distinct identifiers.

## 5.1 Database identifier

Recommended:

```java
@Id
@GeneratedValue
private UUID id;
```

Purpose:

- primary key;
- foreign-key target;
- internal domain identity;
- safe for distributed application architecture;
- avoids exposing sequential database IDs.

The project may keep a numeric primary key if already established, but UUID is preferred for a new system.

## 5.2 Public event code

Recommended field:

```java
@Column(name = "public_id", nullable = false, unique = true, length = 26)
private String publicId;
```

Recommended format:

- ULID, UUIDv7 string, NanoID, or another collision-resistant non-sequential identifier;
- generated only by the server;
- immutable after creation.

Example:

```text
01JX8H7YQAPD4CR2MTB2S12PXW
```

Purpose:

- public API references;
- support cases;
- QR and ticket-domain correlation;
- scanner payload context;
- webhook metadata;
- integrations;
- URLs where the slug is not appropriate.

The QR token should not rely only on this ID. Ticket scanning must use a signed ticket-specific token. The event public ID may be included as signed token context.

## 5.3 Slug

```java
@Column(unique = true, nullable = false, length = 120)
private String slug;
```

Example:

```text
game-of-codes-2026
```

Purpose:

```text
https://passlify.com/events/game-of-codes-2026
```

Slug rules:

- generated by the server from the event name;
- lowercase;
- transliterated when appropriate;
- whitespace converted to hyphens;
- unsupported characters removed;
- consecutive hyphens collapsed;
- globally unique;
- maximum 120 characters;
- reserved words rejected;
- collision resolved by adding a suffix.

Examples:

```text
game-of-codes-2026
game-of-codes-2026-2
game-of-codes-2026-nis
```

Initial recommended behavior:

- generate on creation;
- do not automatically change when the name changes;
- allow explicit slug change while the event is still `DRAFT`;
- after first publication, either make it immutable or preserve redirect history.

Recommended production behavior:

- allow slug changes;
- store old values in `EventSlugRedirect`;
- permanently redirect old links to the current slug.

---

# 6. Event entity fields

Recommended conceptual model:

```java
@Entity
@Table(
    name = "events",
    indexes = {
        @Index(name = "idx_event_organizer", columnList = "organizer_id"),
        @Index(name = "idx_event_organization", columnList = "organization_id"),
        @Index(name = "idx_event_status_visibility", columnList = "status, visibility"),
        @Index(name = "idx_event_starts_at", columnList = "starts_at"),
        @Index(name = "idx_event_public_id", columnList = "public_id", unique = true),
        @Index(name = "idx_event_slug", columnList = "slug", unique = true)
    }
)
public class Event {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, length = 26)
    private String publicId;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String descriptionHtml;

    @Column(name = "description_plain_text", columnDefinition = "text")
    private String descriptionPlainText;

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_mode", nullable = false, length = 20)
    private AttendanceMode attendanceMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "commercial_mode", nullable = false, length = 20)
    private CommercialMode commercialMode;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 20)
    private PaymentProvider paymentProvider;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    @Column(name = "organizer_id", nullable = false, length = 64)
    private String organizerId;

    @Column(name = "organization_id")
    private UUID organizationId;

    private Integer capacity;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags;

    @Version
    private long version;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

---

# 7. Event status lifecycle

## 7.1 Enum

```java
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    COMPLETED
}
```

Use `COMPLETED`, not `FINISHED`, unless the existing codebase already standardizes on `FINISHED`. The API and database must use one term consistently.

## 7.2 State definitions

### DRAFT

The event is being prepared.

Characteristics:

- not publicly listed;
- mutable;
- ticket types may be configured;
- collaborators may be invited;
- publication readiness may be incomplete;
- default state after creation.

### PUBLISHED

The event is active and available according to its visibility rules.

Characteristics:

- public events appear in public discovery;
- private events are accessible through controlled links;
- tickets or registrations may be available;
- business-critical fields become more restricted;
- changes remain possible but must be audited.

### CANCELLED

The organizer or administrator cancelled the event.

Characteristics:

- terminal business state in the initial design;
- no new orders or registrations;
- all ticket sales stop;
- existing attendees should receive cancellation communication;
- refund behavior belongs to the order/payment domain;
- cancellation reason and actor must be recorded.

### COMPLETED

The event has ended and is considered operationally closed.

Characteristics:

- no new sales;
- no standard edits except admin corrections;
- remains available in organizer history;
- may remain publicly visible as an archived event if product policy allows;
- reports remain available.

## 7.3 Allowed transitions

```text
DRAFT -> PUBLISHED
PUBLISHED -> DRAFT       // unpublish
DRAFT -> CANCELLED
PUBLISHED -> CANCELLED
PUBLISHED -> COMPLETED
DRAFT -> COMPLETED       // normally forbidden
CANCELLED -> PUBLISHED   // forbidden by default
COMPLETED -> PUBLISHED   // forbidden by default
```

Recommended transition rules:

| Current | Action | Result | Allowed |
|---|---|---:|---:|
| DRAFT | publish | PUBLISHED | yes |
| PUBLISHED | unpublish | DRAFT | yes, subject to restrictions |
| DRAFT | cancel | CANCELLED | yes |
| PUBLISHED | cancel | CANCELLED | yes |
| PUBLISHED | complete | COMPLETED | yes |
| CANCELLED | restore | DRAFT | admin-only future feature |
| COMPLETED | reopen | PUBLISHED | admin-only exceptional feature |

## 7.4 Automatic completion

A scheduled process may automatically mark an event `COMPLETED` after:

```text
endsAt + configurable grace period
```

Recommended initial grace period:

```text
24 hours
```

Automatic completion must:

- be idempotent;
- produce an audit entry;
- not run for `DRAFT` or `CANCELLED` events;
- emit an `EventCompletedEvent`.

---

# 8. Visibility

## 8.1 Enum

```java
public enum Visibility {
    PRIVATE,
    PUBLIC,
    UNLISTED
}
```

The current implementation may contain only `PRIVATE` and `PUBLIC`, but `UNLISTED` is strongly recommended.

Definitions:

- `PUBLIC`: listed in event discovery and accessible by URL.
- `UNLISTED`: not shown in event discovery but accessible through the direct link.
- `PRIVATE`: requires an invitation, access token, organizer-granted access, or another authorization mechanism.

If private access control is not yet implemented, the system may temporarily interpret `PRIVATE` as “not listed and available only to organizers.” This limitation must be explicit and not confused with a complete private-event invitation system.

## 8.2 Default

```text
PRIVATE
```

## 8.3 Changing visibility

Allowed for `DRAFT` and `PUBLISHED` events.

Every change must record:

- previous value;
- new value;
- user ID;
- timestamp;
- request correlation ID;
- optional reason.

A published public event changed to private or unlisted must disappear from public listings immediately.

---

# 9. Free and paid events

A separate field must express whether the event is commercial.

Do not infer this only from ticket price, because:

- a paid event may not yet have ticket types;
- an event may contain both free and paid ticket types;
- commercial validation is needed before ticket configuration;
- payment-provider rules apply at event level.

## 9.1 Enum

```java
public enum CommercialMode {
    FREE,
    PAID
}
```

Possible future extension:

```java
MIXED
```

Recommended initial rule:

- `FREE` means all ticket types must have price `0`.
- `PAID` means the event may contain paid and optionally complimentary ticket types.

## 9.2 Free event

A free event:

- may be organized by an `INDIVIDUAL`;
- does not require a complete company billing profile;
- must not initiate an external payment session;
- should use `PaymentProvider.MANUAL` only if manual is semantically overloaded, therefore a better design is to introduce `PaymentProvider.NONE`;
- may still create orders or registrations to reserve capacity and issue tickets.

Strong recommendation:

```java
public enum PaymentProvider {
    NONE,
    MOCK,
    MANUAL,
    RAIFFEISEN,
    STRIPE
}
```

Using `MANUAL` as the value for “free event” is not recommended. `NONE` communicates the domain correctly.

## 9.3 Paid event

A paid event requires:

- owning organization kind `COMPANY`;
- complete company legal data;
- administrator-approved payment provider;
- at least one allowed currency;
- selected currency supported by the payment provider;
- payment configuration in an active state;
- organizer authorization to use the assigned provider.

## 9.4 Changing commercial mode

### FREE -> PAID

Allowed only when:

- event is `DRAFT`, or admin approves the change;
- company profile is complete;
- payment provider is assigned;
- currency is supported;
- existing free orders and tickets do not create incompatible state.

### PAID -> FREE

Allowed only when:

- no paid order exists;
- no successful payment exists;
- no refund is pending;
- event is not commercially active.

Otherwise reject with a domain conflict.

---

# 10. Payment-provider assignment

The event creator must not freely assign a payment provider.

## 10.1 Provider enum

Recommended:

```java
public enum PaymentProvider {
    NONE,
    MOCK,
    MANUAL,
    RAIFFEISEN,
    STRIPE
}
```

Meaning:

- `NONE`: free event; no payment processing.
- `MOCK`: local development and automated test provider.
- `MANUAL`: offline or manually reconciled payment, such as bank transfer, cash, invoice, or administrator-confirmed payment.
- `RAIFFEISEN`: production card payment through the Serbian Raiffeisen e-commerce integration.
- `STRIPE`: Stripe Checkout or Payment Intents.

A real Serbian bank integration should have its own provider enum value. Calling it only `MANUAL` loses important domain information.

## 10.2 Provider assignment model

Recommended separate configuration:

```java
OrganizerPaymentCapability {
    UUID id;
    String organizerId;
    UUID organizationId;
    PaymentProvider provider;
    CapabilityStatus status;
    Set<String> allowedCurrencies;
    String merchantConfigurationReference;
    Instant validFrom;
    Instant validUntil;
    String approvedBy;
    Instant approvedAt;
}
```

Capability statuses:

```java
PENDING
ACTIVE
SUSPENDED
REVOKED
EXPIRED
```

An event may select only a provider for which the owning organizer or organization has an `ACTIVE` capability.

## 10.3 Admin behavior

Admin may:

- grant provider access;
- restrict allowed currencies;
- suspend a provider;
- set a default provider;
- change provider before sales begin;
- force provider changes under support procedures;
- inspect who approved the configuration.

## 10.4 Organizer behavior

Organizer may:

- see only providers approved for the organization;
- select among approved providers, if more than one exists;
- not submit arbitrary enum values;
- not use `MOCK` in production;
- not use `RAIFFEISEN` without active bank configuration.

## 10.5 Provider change restrictions

Provider may be changed freely while:

- event is `DRAFT`;
- no payment session exists;
- no successful payment exists.

After payment activity begins, provider change should normally be forbidden.

---

# 11. Currency

## 11.1 Currency field

```java
@Column(nullable = false, length = 3)
private String currency;
```

Use uppercase ISO-4217 codes.

Examples:

- `RSD`
- `EUR`
- `USD`
- `GBP`

## 11.2 Currency ownership

Currency is configured at event level and inherited by all ticket types.

Initial rule:

```text
All ticket types under one event use the same currency.
```

This avoids mixed-currency checkout, reporting, tax, settlement, and refund complexity.

## 11.3 Provider restrictions

Example configuration:

| Provider | Allowed currencies |
|---|---|
| NONE | event display currency or default `RSD` |
| MOCK | configured test currencies |
| MANUAL | administrator-configured |
| RAIFFEISEN | `RSD` initially |
| STRIPE | currencies enabled for the connected Stripe account |

Currency choices shown to the organizer must come from backend configuration. The frontend must not contain the source of truth.

## 11.4 Currency change restrictions

Allowed only when:

- event is `DRAFT`;
- no order exists; or
- every existing order is safely removable test data.

After an order exists, currency is immutable.

---

# 12. Event ownership and organizer display

The system must distinguish:

- the user who owns the event;
- the organization that commercially owns the event;
- the public organizer name displayed on the event page.

## 12.1 Owner user

```java
@Column(name = "organizer_id", nullable = false, length = 64)
private String organizerId;
```

This is the Keycloak subject ID of the event owner.

The initial owner is the authenticated user who created the event.

## 12.2 Organization

```java
@Column(name = "organization_id")
private UUID organizationId;
```

Rules:

- free individual event: organization may reference the user's `INDIVIDUAL` organization;
- company event: references the `COMPANY` organization;
- paid event: must reference a complete `COMPANY` organization;
- organization must belong to or be accessible by the event owner.

## 12.3 Public organizer profile

Recommended event snapshot:

```java
EventOrganizerDisplay {
    String displayName;
    String logoUrl;
    String websiteUrl;
    String description;
}
```

The event page may show:

- company legal name;
- company trading name;
- individual display name;
- event-specific organizer label.

Important design choice:

The public event should not always read live organization values only. Legal and display information may change after ticket sales. A snapshot may be needed for historical correctness.

Recommended approach:

- maintain the organization relation;
- store selected organizer display fields on the event;
- refresh explicitly when the organizer chooses;
- store legal seller snapshot on each order or invoice separately.

---

# 13. Event collaborators and permissions

One event may be managed by multiple users.

## 13.1 Entity

```java
@Entity
@Table(
    name = "event_collaborators",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_event_collaborator_user",
        columnNames = {"event_id", "user_id"}
    )
)
public class EventCollaborator {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus invitationStatus;

    @Column(name = "invited_by", nullable = false, length = 64)
    private String invitedBy;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
```

## 13.2 Roles

```java
public enum EventRole {
    OWNER,
    MANAGER,
    EDITOR,
    VIEWER,
    CHECK_IN_OPERATOR
}
```

Suggested permission matrix:

| Capability | OWNER | MANAGER | EDITOR | VIEWER | CHECK_IN_OPERATOR |
|---|---:|---:|---:|---:|---:|
| View event | yes | yes | yes | yes | limited |
| Edit general details | yes | yes | yes | no | no |
| Edit commercial settings | yes | yes | no | no | no |
| Manage collaborators | yes | yes | no | no | no |
| Transfer ownership | yes | no | no | no | no |
| Publish/unpublish | yes | yes | no | no | no |
| Cancel event | yes | optionally | no | no | no |
| Configure tickets | yes | yes | yes | no | no |
| View reports | yes | yes | optionally | yes | no |
| Scan tickets | yes | yes | optionally | no | yes |

The initial implementation may simplify this to `OWNER`, `MANAGER`, and `VIEWER`, but the database and authorization service should be extendable.

## 13.3 Invitation flow

1. Owner enters collaborator email.
2. Backend validates permissions and duplicates.
3. Invitation record is created.
4. Notification email is sent.
5. Existing Keycloak user accepts.
6. If the user does not exist, they register first.
7. Invitation is linked to the Keycloak subject after acceptance.
8. Every acceptance, role change, and removal is audited.

Invitation statuses:

```java
PENDING
ACCEPTED
REVOKED
EXPIRED
```

## 13.4 Ownership transfer

Ownership transfer is a special operation.

Rules:

- only current `OWNER` or platform `ADMIN`;
- target must be an accepted collaborator;
- target must have organizer capability;
- paid event ownership transfer may require organization reassignment validation;
- action requires explicit confirmation;
- old owner becomes `MANAGER` by default, unless removed;
- complete audit entry required.

---

# 14. Attendance mode and location

An event may be:

```java
public enum AttendanceMode {
    IN_PERSON,
    ONLINE,
    HYBRID
}
```

## 14.1 In-person event

Requires a physical location.

## 14.2 Online event

Requires online access configuration.

## 14.3 Hybrid event

Requires both physical and online configuration.

## 14.4 Location model

Recommended reusable entity:

```java
@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "venue_name", length = 255)
    private String venueName;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 255)
    private String street;

    @Column(name = "street_number", length = 30)
    private String streetNumber;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(length = 255)
    private String district;

    @Column(name = "full_address", length = 500)
    private String fullAddress;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "maps_url", length = 2048)
    private String mapsUrl;

    @Column(name = "accessibility_notes", columnDefinition = "text")
    private String accessibilityNotes;

    @Column(name = "entrance_notes", columnDefinition = "text")
    private String entranceNotes;
}
```

Recommended fields:

- location/venue name;
- country;
- city;
- postal code;
- street;
- street number;
- second address line;
- full formatted address;
- latitude;
- longitude;
- Google Maps or map-provider URL;
- accessibility notes;
- entrance instructions.

## 14.5 Online configuration

Recommended:

```java
EventOnlineAccess {
    String publicUrl;
    String platformName;
    String accessInstructionsHtml;
    boolean revealOnlyAfterRegistration;
    Instant revealAt;
}
```

Sensitive meeting passwords and private links should not be exposed on the public event entity. Store secrets separately and reveal them only to eligible attendees.

## 14.6 Hybrid event

A hybrid event uses both a `Location` and online-access configuration.

## 14.7 Location reuse

Locations may be reusable across events, but organizers must not accidentally edit a shared location and thereby alter historical events.

Recommended options:

- copy location values into an event-owned location record; or
- use versioned immutable venue records.

For MVP, an event-owned `Location` is simplest and safest.

---

# 15. Event capacity

```java
private Integer capacity;
```

Meaning:

Maximum number of attendees allowed for the event as a whole.

Rules:

- nullable means no explicit event-level cap;
- must be positive when provided;
- event capacity is independent of physical venue capacity;
- total ticket-type sellable quantities must not exceed event capacity unless overbooking is explicitly supported;
- free guest-list entries, complimentary tickets, and manually added attendees must count toward capacity according to later ticket-domain rules;
- decreasing capacity below already committed attendance must be rejected;
- capacity changes must be audited.

Capacity is not the sole inventory source. Ticket types own sales quantities, while event capacity provides an aggregate ceiling.

---

# 16. Date, time, and timezone

Fields:

```java
private Instant startsAt;
private Instant endsAt;
private String timezone;
```

## 16.1 Rules

- `startsAt` required;
- `endsAt` required;
- `endsAt` must be after `startsAt`;
- timezone required as IANA zone ID;
- example: `Europe/Belgrade`;
- API accepts ISO-8601 offset date-times or local time plus timezone;
- database stores instants in UTC;
- public responses include timezone;
- frontend renders event-local time.

## 16.2 Why timezone is mandatory

An instant alone is insufficient for:

- displaying event-local times;
- daylight-saving transitions;
- recurring communication;
- sale windows;
- calendar export;
- online events with international audiences.

## 16.3 Editing dates

Date changes after publication are allowed only with:

- organizer-manager permission;
- complete audit entry;
- attendee notification if registrations exist;
- revalidation of ticket sale windows;
- optional explicit confirmation.

Changing an event date with paid orders should emit an `EventScheduleChangedEvent`.

---

# 17. Event name, description, and media

## 17.1 Name

```java
@Column(nullable = false, length = 255)
private String name;
```

Rules:

- required;
- trim leading/trailing whitespace;
- minimum recommended length: 3;
- maximum 255;
- plain text only;
- no HTML.

## 17.2 Rich-text description

Store sanitized HTML:

```java
@Column(name = "description_html", columnDefinition = "text")
private String descriptionHtml;
```

Recommended additional field:

```java
@Column(name = "description_plain_text", columnDefinition = "text")
private String descriptionPlainText;
```

Purpose of plain text:

- search indexing;
- email preview;
- SEO description;
- accessibility fallback;
- exports.

## 17.3 Allowed formatting

Suggested initial allowlist:

- paragraphs;
- line breaks;
- bold;
- italic;
- underline if required;
- unordered lists;
- ordered lists;
- list items;
- headings `h2` to `h4`;
- blockquotes;
- safe external links.

Disallow:

- scripts;
- inline JavaScript;
- arbitrary iframes;
- forms;
- style tags;
- event handlers;
- unsafe protocols;
- embedded tracking pixels.

Sanitize on the server even when the frontend editor already sanitizes.

## 17.4 Cover image

```java
@Column(name = "cover_image_url", length = 2048)
private String coverImageUrl;
```

Recommended production model:

- upload media through a Passlify media endpoint;
- store object-storage key and public CDN URL;
- validate content type;
- validate maximum size;
- generate thumbnails;
- store width, height, and alt text;
- prevent arbitrary dangerous external URLs if possible.

Suggested requirements:

- JPEG, PNG, WebP;
- maximum 10 MB source;
- recommended aspect ratio 16:9;
- minimum dimensions 1200×675;
- alt text available for accessibility.

---

# 18. Event contacts and social links

Event contact data must be modeled separately from the organization profile because each event may have different operational contacts.

## 18.1 Entity or embeddable

```java
@Embeddable
public class EventContact {

    @Column(name = "contact_email", length = 320)
    private String email;

    @Column(name = "contact_phone", length = 40)
    private String phone;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Column(name = "facebook_url", length = 2048)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 2048)
    private String instagramUrl;

    @Column(name = "youtube_url", length = 2048)
    private String youtubeUrl;

    @Column(name = "linkedin_url", length = 2048)
    private String linkedinUrl;

    @Column(name = "tiktok_url", length = 2048)
    private String tiktokUrl;

    @Column(name = "x_url", length = 2048)
    private String xUrl;
}
```

## 18.2 Validation

- email must be syntactically valid;
- phone should be stored in E.164 where possible;
- URLs must use `https`, with narrowly justified exceptions;
- URLs must not contain JavaScript or unsafe schemes;
- blank values stored as `null`;
- social URLs may optionally be validated against expected domains;
- at least one contact method should be required before publication.

## 18.3 Public exposure

The organizer chooses which contact fields are public.

Recommended field-level flags:

```java
boolean showEmail;
boolean showPhone;
boolean showWebsite;
boolean showSocialLinks;
```

A private operational email should not automatically become public.

---

# 19. Event type and classification

Events are classified through administrator-managed reference data.

## 19.1 Event type structure

The screenshot indicates grouped types such as:

- Sport
  - Paintball
  - Rafting
  - Running / Marathon
  - Tournaments
- Music
  - Live concert
  - Festival
  - DJ party
  - Party
- Culture
  - Exhibition
  - Movie
  - Show
  - Stand-up comedy
  - Quizzes
- Business
  - Conference
  - Seminar
- Other
  - Networking
  - Workshop
  - Season
  - Other
- Private event
  - Private event

Recommended hierarchical model:

```java
@Entity
@Table(name = "event_types")
public class EventType {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private EventType parent;

    @Column(nullable = false)
    private boolean selectable;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
```

Examples:

```text
SPORT
SPORT.RUNNING
MUSIC
MUSIC.FESTIVAL
BUSINESS
BUSINESS.CONFERENCE
```

The organizer selects a leaf event type. Category headings are non-selectable.

## 19.2 Administration

Only admin may:

- create types;
- rename types;
- reorder types;
- deactivate types;
- create parent-child relationships.

Types referenced by existing events should be deactivated, not deleted.

## 19.3 Tags

Tags complement event types.

```java
List<String> tags;
```

Rules:

- optional;
- maximum recommended count: 15;
- each tag maximum 40 characters;
- normalized and trimmed;
- duplicates ignored case-insensitively;
- do not replace event type.

Examples:

```text
java
artificial-intelligence
nis
software-architecture
```

---

# 20. Event properties

The screenshot shows structured event conditions. These must be modeled as fields rather than plain description text.

Suggested settings:

```java
@Entity
@Table(name = "event_settings")
public class EventSettings {

    @Id
    private UUID eventId;

    @OneToOne
    @MapsId
    private Event event;

    @Column(name = "minimum_age")
    private Integer minimumAge;

    @Column(name = "tickets_available_at_entrance", nullable = false)
    private boolean ticketsAvailableAtEntrance;

    @Column(name = "visitor_country_restriction_enabled", nullable = false)
    private boolean visitorCountryRestrictionEnabled;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_visitor_country_codes", columnDefinition = "text[]")
    private List<String> allowedVisitorCountryCodes;

    @Column(name = "multiple_entry_allowed", nullable = false)
    private boolean multipleEntryAllowed;

    @Column(name = "people_with_disabilities_free_entry", nullable = false)
    private boolean peopleWithDisabilitiesFreeEntry;

    @Column(name = "children_free_entry_age")
    private Integer childrenFreeEntryAge;

    @Column(name = "terms_html", columnDefinition = "text")
    private String termsHtml;

    @Column(name = "additional_rules_html", columnDefinition = "text")
    private String additionalRulesHtml;
}
```

## 20.1 Minimum age

Represents:

```text
Entry allowed to those older than [age]
```

Recommended field:

```java
Integer minimumAge;
```

Rules:

- nullable means no age restriction;
- valid range `0..120`;
- clarify semantics as `minimumAgeInclusive`.

Better UI label:

```text
Minimum attendee age
```

Avoid ambiguous wording such as “older than 18,” which may mean either `>=18` or `>18`.

## 20.2 Tickets at the entrance

```java
boolean ticketsAvailableAtEntrance;
```

This is informational unless Passlify later provides box-office sales.

Future extensions:

- entrance ticket price;
- entrance ticket inventory;
- entrance sales time;
- payment methods;
- box-office operator permissions.

## 20.3 Visitor-country restrictions

The screenshot says:

```text
Tickets are valid only for visitors from ...
```

Recommended fields:

```java
boolean visitorCountryRestrictionEnabled;
List<String> allowedVisitorCountryCodes;
```

Country codes must be ISO-3166-1 alpha-2.

Important:

This setting affects eligibility, but the system cannot reliably enforce it unless attendee or buyer country information is collected and verified. Publication readiness should warn the organizer when restriction is enabled but no country field exists in attendee forms.

## 20.4 Multiple entry

```java
boolean multipleEntryAllowed;
```

This setting has major implications for scanning and cannot remain only descriptive.

Initial semantics:

- `false`: first valid scan changes ticket from `VALID` to `USED`; later scans are rejected.
- `true`: ticket remains usable according to an entry policy.

A boolean alone is not sufficient for advanced usage. Recommended future model:

```java
EntryPolicy {
    SINGLE_USE,
    UNLIMITED_REENTRY,
    MAX_ENTRIES,
    DAILY_ENTRY,
    SESSION_BASED
}
```

For the current Event specification, store the boolean but document that the scan module must be upgraded before enabling it in production.

## 20.5 People with disabilities enter free

```java
boolean peopleWithDisabilitiesFreeEntry;
```

This should initially be informational and should not automatically generate a free ticket without a defined verification process.

Future requirements may include:

- eligible ticket type;
- proof/verification workflow;
- companion rules;
- capacity reservation.

## 20.6 Free entry for children

```java
Integer childrenFreeEntryAge;
```

Meaning:

```text
Children under X enter free.
```

Rules:

- nullable means not offered;
- range `0..18` or configurable;
- semantics should explicitly mean age `< X`;
- informational unless linked to a child ticket type and verification flow.

## 20.7 Additional event rules

Use sanitized HTML for rules not covered by structured fields.

Structured fields remain the source of truth whenever a dedicated setting exists.

---

# 21. Event creation flow

## 21.1 Minimal creation

Recommended API:

```http
POST /api/v1/events
```

Minimal request:

```json
{
  "name": "Game of Codes 2026",
  "startsAt": "2026-10-09T07:00:00Z",
  "endsAt": "2026-10-10T16:00:00Z",
  "timezone": "Europe/Belgrade",
  "attendanceMode": "IN_PERSON",
  "commercialMode": "FREE",
  "currency": "RSD"
}
```

Server behavior:

1. authenticate caller;
2. verify `ORGANIZER` role;
3. load or create caller organization profile;
4. generate UUID;
5. generate public ID;
6. generate unique slug;
7. set status `DRAFT`;
8. set visibility `PRIVATE`;
9. set payment provider:
   - `NONE` for free event;
   - unresolved or admin-assigned for paid event;
10. set owner;
11. create `OWNER` collaborator entry;
12. initialize default event settings;
13. store audit event `EVENT_CREATED`;
14. return created resource.

## 21.2 Full wizard

The frontend may use a multi-step wizard:

1. Basic information
2. Date and event format
3. Location or online access
4. Organizer
5. Free or paid
6. Payment and currency
7. Event type
8. Contact and social links
9. Event rules
10. Review and publish

The backend must not depend on the frontend wizard order. Draft updates may arrive in any sequence.

---

# 22. Event update model

Recommended endpoint:

```http
PATCH /api/v1/events/{eventId}
```

Use a typed request rather than exposing the JPA entity.

Example:

```json
{
  "name": "Game of Codes 2026",
  "descriptionHtml": "<p>Two days of software engineering...</p>",
  "coverImageUrl": "https://cdn.passlify.com/events/...",
  "visibility": "PUBLIC",
  "startsAt": "2026-10-09T07:00:00Z",
  "endsAt": "2026-10-10T16:00:00Z",
  "timezone": "Europe/Belgrade",
  "eventTypeId": "d5eb...",
  "capacity": 250,
  "tags": ["java", "ai", "cloud"]
}
```

## 22.1 Concurrency

Use optimistic locking:

```java
@Version
private long version;
```

Request may contain:

```json
{
  "version": 7
}
```

When two collaborators edit concurrently, stale updates should return:

```http
409 Conflict
```

Problem type:

```text
event-version-conflict
```

Do not silently overwrite changes.

## 22.2 Patch semantics

- omitted field: unchanged;
- explicit null: clear field if nullable;
- immutable fields rejected;
- all validation performed after applying the proposed patch;
- audit stores only changed values.

---

# 23. Publication readiness

Publication is a business operation, not a generic status patch.

Endpoint:

```http
POST /api/v1/events/{eventId}/publish
```

The backend must execute a publication-readiness validator.

## 23.1 Required for every event

- name present;
- valid slug;
- valid public ID;
- start and end times valid;
- timezone valid;
- event type selected;
- attendance mode configured;
- corresponding location/online settings complete;
- contact method present;
- organizer display data present;
- visibility valid;
- no unresolved validation errors.

## 23.2 Additional paid-event requirements

- commercial mode `PAID`;
- company organization;
- complete legal company profile;
- active payment-provider capability;
- provider assigned;
- currency supported;
- at least one ticket type;
- at least one sellable ticket type;
- ticket prices compatible with event currency;
- payment configuration complete.

## 23.3 Additional free-event requirements

- payment provider `NONE`;
- all configured ticket types have zero price;
- no payment-only requirement.

## 23.4 Publication response

Success:

```http
200 OK
```

Failure:

```http
422 Unprocessable Entity
```

Example problem:

```json
{
  "type": "https://api.passlify.com/problems/event-not-ready-for-publication",
  "title": "Event is not ready for publication",
  "status": 422,
  "detail": "The event has 3 unresolved publication requirements.",
  "eventId": "01JX...",
  "violations": [
    {
      "code": "LOCATION_REQUIRED",
      "field": "location",
      "message": "An in-person event requires a physical location."
    },
    {
      "code": "CONTACT_REQUIRED",
      "field": "contact",
      "message": "At least one event contact method is required."
    }
  ]
}
```

## 23.5 Readiness preview

Recommended endpoint:

```http
GET /api/v1/events/{eventId}/publication-readiness
```

This enables the UI to show a checklist before the organizer attempts publication.

---

# 24. Unpublish, cancel, and complete

## 24.1 Unpublish

```http
POST /api/v1/events/{eventId}/unpublish
```

Rules:

- only `PUBLISHED`;
- owner or manager;
- if orders exist, return warning or require explicit confirmation;
- immediately remove from public discovery;
- stop new checkout;
- do not invalidate existing tickets;
- audit reason.

Request:

```json
{
  "reason": "Event details are being updated.",
  "confirmImpact": true
}
```

## 24.2 Cancel

```http
POST /api/v1/events/{eventId}/cancel
```

Request:

```json
{
  "reason": "The venue is no longer available.",
  "notifyAttendees": true
}
```

Rules:

- reason required;
- no new sales;
- event state becomes `CANCELLED`;
- emit event for order, payment, notification, and ticket modules;
- idempotent repeated cancellation;
- organizer cannot directly set status by PATCH.

## 24.3 Complete

```http
POST /api/v1/events/{eventId}/complete
```

Rules:

- event must be published;
- end time normally passed;
- admin may force completion;
- stop sales;
- preserve reporting;
- audit action.

---

# 25. Public event API

## 25.1 Public list

```http
GET /api/v1/public/events
```

Returns only:

- `PUBLISHED`;
- `PUBLIC`;
- within product-defined archival window;
- not blocked by admin.

Suggested filters:

- event type;
- city;
- country;
- start date;
- attendance mode;
- free/paid;
- tags;
- search text.

## 25.2 Public detail

```http
GET /api/v1/public/events/{slug}
```

Response should not expose:

- internal database ID unless needed;
- owner Keycloak subject;
- organization internals;
- payment credentials;
- private contact details;
- draft-only fields;
- collaborator data;
- audit data;
- online secrets.

Public response may include:

- public ID;
- slug;
- name;
- sanitized description;
- cover image;
- schedule;
- timezone;
- location;
- attendance mode;
- organizer display;
- public contacts;
- event type;
- public tags;
- event properties;
- ticket availability summary.

## 25.3 Private and unlisted resolution

- `UNLISTED`: direct slug works; not returned in list.
- `PRIVATE`: return 404 for unauthorized caller to avoid leaking existence.
- organizer/collaborator access uses authenticated management API.

---

# 26. Organizer event API

Recommended endpoints:

```http
POST   /api/v1/events
GET    /api/v1/events
GET    /api/v1/events/{eventId}
PATCH  /api/v1/events/{eventId}

POST   /api/v1/events/{eventId}/publish
POST   /api/v1/events/{eventId}/unpublish
POST   /api/v1/events/{eventId}/cancel
POST   /api/v1/events/{eventId}/complete

GET    /api/v1/events/{eventId}/publication-readiness

GET    /api/v1/events/{eventId}/collaborators
POST   /api/v1/events/{eventId}/collaborators
PATCH  /api/v1/events/{eventId}/collaborators/{collaboratorId}
DELETE /api/v1/events/{eventId}/collaborators/{collaboratorId}
POST   /api/v1/events/{eventId}/transfer-ownership

GET    /api/v1/events/{eventId}/audit
```

Optional focused endpoints:

```http
PUT /api/v1/events/{eventId}/location
PUT /api/v1/events/{eventId}/contact
PUT /api/v1/events/{eventId}/settings
PUT /api/v1/events/{eventId}/commercial-settings
```

Focused endpoints are recommended when permission rules differ by section.

---

# 27. Authorization rules

## 27.1 General

Every management operation checks:

1. caller is authenticated;
2. caller has global role allowing the action;
3. caller has event-level permission;
4. event belongs to the caller, their organization, or accepted collaborator relation;
5. state permits the operation.

## 27.2 Never trust request ownership

The create request must not accept arbitrary `organizerId`.

The backend derives owner from:

```text
JWT subject
```

`organizationId` may be supplied only from organizations the caller is authorized to use.

## 27.3 Admin override

Admin operations must still be audited with:

- admin ID;
- reason;
- source IP where available;
- correlation ID;
- before/after values.

---

# 28. Audit

`createdAt` and `updatedAt` are not enough.

The system needs both:

1. current-row audit fields; and
2. immutable event change history.

## 28.1 Current-row fields

```java
createdAt
createdBy
updatedAt
updatedBy
```

## 28.2 Event audit table

```java
@Entity
@Table(
    name = "event_audit_entries",
    indexes = {
        @Index(name = "idx_event_audit_event_time", columnList = "event_id, occurred_at")
    }
)
public class EventAuditEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_public_id", nullable = false, length = 26)
    private String eventPublicId;

    @Column(name = "actor_user_id", nullable = false, length = 64)
    private String actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private EventAuditAction action;

    @Column(name = "changed_fields", columnDefinition = "jsonb")
    private String changedFields;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
```

## 28.3 Example changed-fields JSON

```json
{
  "visibility": {
    "from": "PUBLIC",
    "to": "PRIVATE"
  },
  "capacity": {
    "from": 200,
    "to": 250
  }
}
```

## 28.4 Audit actions

Suggested values:

```text
EVENT_CREATED
EVENT_UPDATED
EVENT_PUBLISHED
EVENT_UNPUBLISHED
EVENT_CANCELLED
EVENT_COMPLETED
VISIBILITY_CHANGED
COMMERCIAL_MODE_CHANGED
PAYMENT_PROVIDER_ASSIGNED
CURRENCY_CHANGED
LOCATION_CHANGED
SCHEDULE_CHANGED
CONTACT_CHANGED
SETTINGS_CHANGED
COLLABORATOR_INVITED
COLLABORATOR_ACCEPTED
COLLABORATOR_ROLE_CHANGED
COLLABORATOR_REMOVED
OWNERSHIP_TRANSFERRED
ADMIN_OVERRIDE
```

## 28.5 Audit access

- owner and manager may view relevant event audit history;
- admin may view full detail;
- sensitive internal details may be hidden from regular organizers;
- audit entries are immutable;
- audit deletion is forbidden through normal application APIs.

---

# 29. Domain events

The Event module should publish internal domain events.

Suggested events:

```text
EventCreatedEvent
EventUpdatedEvent
EventPublishedEvent
EventUnpublishedEvent
EventCancelledEvent
EventCompletedEvent
EventScheduleChangedEvent
EventVisibilityChangedEvent
EventLocationChangedEvent
EventCommercialConfigurationChangedEvent
EventCollaboratorInvitedEvent
EventOwnershipTransferredEvent
```

Consumers may include:

- notification;
- search indexing;
- cache invalidation;
- order;
- payment;
- ticket issuance;
- scanning;
- dashboard;
- analytics;
- webhooks.

Do not perform all cross-module side effects directly in the event transaction. Use transactional domain events or an outbox pattern where reliability matters.

---

# 30. Validation rules

## 30.1 General

- all strings trimmed;
- blank converted to `null` where optional;
- enum values strict;
- date/time rules validated;
- ownership validated;
- URLs validated;
- HTML sanitized;
- country and currency codes standardized;
- provider/currency compatibility validated;
- status changes through dedicated operations only.

## 30.2 Field validation summary

| Field | Rule |
|---|---|
| name | required, 3–255 chars |
| slug | required, globally unique, normalized |
| publicId | server generated, unique, immutable |
| descriptionHtml | sanitized, configurable max size |
| coverImageUrl | valid safe URL or managed-media reference |
| startsAt | required |
| endsAt | after startsAt |
| timezone | valid IANA ID |
| status | server-controlled |
| visibility | enum |
| attendanceMode | enum |
| commercialMode | enum |
| currency | uppercase ISO-4217 |
| paymentProvider | admin-approved |
| capacity | positive or null |
| eventType | active selectable type |
| tags | max count and length |
| organizerId | server-controlled |
| organizationId | caller-authorized organization |

## 30.3 Suggested description limits

- HTML source: 100,000 characters;
- plain-text result: 50,000 characters;
- contact URLs: 2,048 characters;
- cancellation reason: 1,000 characters.

---

# 31. Error handling

Use RFC 7807.

Suggested problem codes:

```text
event-not-found
event-access-denied
event-invalid-state-transition
event-not-ready-for-publication
event-version-conflict
event-slug-conflict
event-invalid-schedule
event-invalid-location
event-invalid-contact
event-company-required
event-payment-provider-not-approved
event-currency-not-supported
event-commercial-mode-conflict
event-capacity-below-committed-count
event-collaborator-already-exists
event-owner-cannot-be-removed
event-type-inactive
```

Security-sensitive behavior:

- unauthorized access to a private event should often return `404`, not `403`, to prevent resource enumeration;
- authenticated collaborator permission failures may return `403`.

---

# 32. Database design and migrations

Recommended migrations:

```text
V5__extend_event_identity_and_audit.sql
V6__create_event_settings.sql
V7__create_event_contacts.sql
V8__create_event_collaborators.sql
V9__create_event_audit_entries.sql
V10__extend_event_types_hierarchy.sql
V11__create_payment_capabilities.sql
V12__create_event_slug_redirects.sql
```

## 32.1 Event indexes

Recommended:

```sql
create unique index uk_events_public_id on events(public_id);
create unique index uk_events_slug on events(lower(slug));
create index idx_events_owner on events(organizer_id);
create index idx_events_organization on events(organization_id);
create index idx_events_public_listing
    on events(status, visibility, starts_at);
create index idx_events_type on events(event_type_id);
```

## 32.2 Case-insensitive slug uniqueness

Use either:

- normalized lowercase writes plus unique constraint; or
- unique index on `lower(slug)`.

## 32.3 Existing data migration

For existing events:

- generate public IDs;
- populate timezone from known defaults;
- set commercial mode based on ticket prices if safely inferable;
- set free events to provider `NONE`;
- preserve existing provider for paid events;
- create owner collaborator records;
- initialize settings with false/null defaults;
- create baseline audit entry such as `EVENT_MIGRATED`.

---

# 33. Service architecture

Recommended services:

```text
EventService
EventQueryService
EventLifecycleService
EventPublicationService
EventPublicationReadinessValidator
EventAuthorizationService
EventCommercialPolicyService
EventSlugService
EventLocationService
EventContactService
EventSettingsService
EventCollaboratorService
EventAuditService
EventTypeService
PaymentCapabilityService
HtmlSanitizationService
```

Avoid one giant `EventService`.

## 33.1 Validator architecture

Use focused validators:

```text
EventCoreValidator
EventScheduleValidator
EventLocationValidator
EventContactValidator
EventCommercialValidator
EventPublicationValidator
EventStateTransitionValidator
EventCollaboratorValidator
```

Validators must express business rules and return structured violations.

---

# 34. DTO design

Do not return JPA entities directly.

Suggested DTOs:

```text
CreateEventRequest
UpdateEventRequest
EventManagementResponse
EventSummaryResponse
PublicEventResponse
EventLocationRequest
EventContactRequest
EventSettingsRequest
EventCommercialSettingsRequest
PublicationReadinessResponse
CancelEventRequest
UnpublishEventRequest
AddCollaboratorRequest
UpdateCollaboratorRoleRequest
TransferOwnershipRequest
EventAuditResponse
```

Public and organizer responses must be separate.

---

# 35. User stories

## US-EVT-001 — Create a draft event

**As an organizer**, I want to create a new event so that I can configure it before making it visible.

### Acceptance criteria

```gherkin
Given an authenticated user with the ORGANIZER role
When the user creates an event with valid minimum data
Then the system creates a new event
And assigns the authenticated user as owner
And generates an immutable public ID
And generates a globally unique slug
And sets status to DRAFT
And sets visibility to PRIVATE
And creates an OWNER collaborator record
And records an EVENT_CREATED audit entry
```

```gherkin
Given an authenticated user without the ORGANIZER role
When the user attempts to create an event
Then the system returns 403 Forbidden
```

## US-EVT-002 — Edit event details

**As an authorized event editor**, I want to edit event details so that the event page remains accurate.

### Acceptance criteria

```gherkin
Given a draft event and an authorized editor
When valid event fields are updated
Then the changes are persisted
And updatedAt and updatedBy are changed
And an audit entry records only the changed fields
```

```gherkin
Given a stale event version
When an editor submits an update
Then the system returns 409 Conflict
And does not overwrite the newer version
```

## US-EVT-003 — Configure event format and location

**As an organizer**, I want to configure an in-person, online, or hybrid event so that attendees know how to participate.

### Acceptance criteria

```gherkin
Given attendance mode IN_PERSON
When the organizer attempts to publish without a physical location
Then publication is rejected with LOCATION_REQUIRED
```

```gherkin
Given attendance mode ONLINE
When online access configuration is complete
Then a physical location is not required
```

```gherkin
Given attendance mode HYBRID
When either physical or online configuration is missing
Then publication is rejected
```

## US-EVT-004 — Configure a free event

**As an organizer**, I want to publish a free event without a company profile so that community registrations are supported.

### Acceptance criteria

```gherkin
Given an individual organizer
And an event with commercial mode FREE
And all ticket types are free
When all publication requirements are satisfied
Then the event may be published
And no external payment provider is required
```

## US-EVT-005 — Configure a paid event

**As an organizer**, I want to sell paid tickets when my company and payment configuration are approved.

### Acceptance criteria

```gherkin
Given an event with commercial mode PAID
And the organizer has no complete COMPANY organization
When publication is requested
Then publication is rejected with EVENT_COMPANY_REQUIRED
```

```gherkin
Given a complete COMPANY organization
And an active approved payment capability
And a supported currency
When the paid event is published
Then the event becomes PUBLISHED
```

## US-EVT-006 — Admin assigns payment provider

**As a platform administrator**, I want to control which payment providers an organization may use so that unauthorized integrations cannot process money.

### Acceptance criteria

```gherkin
Given an organization without Stripe capability
When its organizer attempts to select STRIPE
Then the request is rejected
```

```gherkin
Given an admin grants active STRIPE capability with EUR
When the organizer configures STRIPE and EUR
Then the configuration is accepted
```

## US-EVT-007 — Manage collaborators

**As an event owner**, I want to invite additional users to manage my event so that event administration is shared.

### Acceptance criteria

```gherkin
Given an event owner
When the owner invites a new collaborator by email
Then a pending invitation is created
And a notification is sent
And an audit entry is stored
```

```gherkin
Given an editor collaborator
When the editor attempts to manage payment configuration
Then the system returns 403 Forbidden
```

## US-EVT-008 — Publish event

**As an event manager**, I want to publish a complete event so that it becomes available to its intended audience.

### Acceptance criteria

```gherkin
Given a complete public draft event
When an authorized manager publishes it
Then status changes to PUBLISHED
And the event becomes available through the public API
And an EVENT_PUBLISHED audit entry is stored
```

```gherkin
Given an incomplete event
When publication is requested
Then status remains DRAFT
And structured readiness violations are returned
```

## US-EVT-009 — Change visibility

**As an event manager**, I want to change whether the event is public, unlisted, or private.

### Acceptance criteria

```gherkin
Given a published public event
When visibility changes to PRIVATE
Then it disappears from public listings immediately
And the change is audited
```

## US-EVT-010 — Cancel event

**As an event owner**, I want to cancel an event with a reason so that sales stop and downstream systems can react.

### Acceptance criteria

```gherkin
Given a published event
When the owner cancels it with a reason
Then status becomes CANCELLED
And new orders are rejected
And an EventCancelledEvent is emitted
And cancellation is audited
```

## US-EVT-011 — Store rich description safely

**As an organizer**, I want to format the event description so that the page is readable and professional.

### Acceptance criteria

```gherkin
Given description HTML containing allowed formatting
When the event is saved
Then allowed formatting is preserved
And a plain-text representation is stored
```

```gherkin
Given description HTML containing script tags or unsafe attributes
When the event is saved
Then unsafe content is removed or rejected
And no executable script is stored
```

## US-EVT-012 — Configure event conditions

**As an organizer**, I want to configure age, child-entry, accessibility, country, and re-entry rules so that event conditions are clear.

### Acceptance criteria

```gherkin
Given a minimum age value outside the accepted range
When settings are saved
Then the request is rejected
```

```gherkin
Given country restriction is enabled
And no allowed country is provided
When settings are saved
Then the request is rejected
```

## US-EVT-013 — View audit history

**As an event owner**, I want to see who changed important event settings so that event administration is accountable.

### Acceptance criteria

```gherkin
Given several event updates by different collaborators
When the owner requests audit history
Then entries are returned in reverse chronological order
And each entry identifies actor, action, time, and changed fields
```

---

# 36. Testing strategy

## 36.1 Unit tests

Test:

- slug generation and collisions;
- date validation;
- state transitions;
- free/paid rules;
- provider authorization;
- currency support;
- publication readiness;
- collaborator permission matrix;
- event-setting validation;
- HTML sanitization;
- capacity rules;
- audit diff generation.

## 36.2 Repository tests

Use PostgreSQL Testcontainers.

Test:

- unique slug;
- unique public ID;
- collaborator uniqueness;
- JSONB audit storage;
- array tags;
- optimistic locking;
- public listing query;
- case-insensitive slug behavior.

## 36.3 Integration tests

Test complete flows:

1. organizer creates free event;
2. organizer fills required data;
3. organizer publishes;
4. public client retrieves event;
5. visibility changes to private;
6. public client receives not found;
7. collaborator is invited and accepts;
8. collaborator updates event;
9. audit identifies collaborator;
10. organizer cancels event.

Paid flow:

1. individual attempts paid publication and fails;
2. company profile completed;
3. admin enables provider;
4. organizer selects supported currency;
5. paid event publishes;
6. unsupported currency is rejected.

## 36.4 Security tests

- user cannot access unrelated event;
- collaborator cannot exceed assigned role;
- organizer cannot assign arbitrary payment provider;
- organizer cannot spoof owner ID;
- private events do not leak;
- unsafe HTML does not execute;
- admin override is audited.

## 36.5 Concurrency tests

- two editors update same version;
- slug generated concurrently;
- collaborator invited twice concurrently;
- publish and edit conflict;
- cancel and purchase race is handled by downstream order checks.

---

# 37. Observability

Recommended metrics:

```text
passlify_events_created_total
passlify_events_published_total
passlify_events_cancelled_total
passlify_event_publication_failures_total
passlify_event_updates_total
passlify_event_collaborator_invitations_total
passlify_event_state_transition_duration
```

Recommended log fields:

```text
eventId
eventPublicId
organizationId
actorUserId
action
oldStatus
newStatus
requestId
```

Never log:

- payment secrets;
- private online access passwords;
- complete sensitive contact data;
- unredacted personal information unnecessarily.

---

# 38. Security considerations

- sanitize rich text on the server;
- use managed media uploads where possible;
- use authorization service for every event operation;
- never trust organizer or organization IDs from the client;
- private online links stored separately;
- rate-limit public slug lookup and collaborator invitations;
- prevent collaborator email enumeration;
- use signed invitation tokens;
- use optimistic locking;
- audit administrator overrides;
- validate SSRF risk if arbitrary external URLs are fetched;
- do not place secrets inside event description or public metadata.

---

# 39. Recommended implementation phases

## Phase 1 — Event foundation

- event public ID;
- timezone;
- attendance mode;
- free/paid mode;
- payment provider `NONE`;
- event contact;
- event settings;
- event type hierarchy;
- audit fields;
- audit history;
- publication readiness.

## Phase 2 — Collaboration

- event collaborators;
- invitation flow;
- permission matrix;
- ownership transfer;
- collaborator audit.

## Phase 3 — Commercial control

- organizer payment capabilities;
- admin provider assignment;
- provider-currency rules;
- paid-event publication gate;
- production bank and Stripe configuration.

## Phase 4 — Advanced event lifecycle

- slug redirects;
- automated completion;
- attendee schedule-change notifications;
- private-event invitations;
- unlisted events;
- event archival.

---

# 40. Explicit decisions for Passlify

The following decisions should be treated as the intended Passlify behavior unless later changed:

1. Organizers may create unlimited events.
2. New events are `DRAFT` and `PRIVATE`.
3. Event owner is derived from the JWT.
4. An event supports multiple collaborators.
5. Event collaborator access is role-based.
6. Free events are supported without a company.
7. Paid events require a complete company profile.
8. Payment providers are approved by administrators.
9. Organizers may only choose among approved providers.
10. Raiffeisen production payments initially support only `RSD`.
11. Stripe currencies come from platform/provider configuration.
12. Event currency is shared by all ticket types.
13. Currency becomes immutable after the first order.
14. Payment provider becomes immutable after payment activity begins.
15. Events support in-person, online, and hybrid formats.
16. Event description is sanitized HTML.
17. Every material event change is audited.
18. The public URL is based on a generated unique slug.
19. The event also has an immutable non-sequential public ID.
20. Structured event properties are stored separately from description.
21. Event contact information is event-specific.
22. Event type is selected from administrator-managed hierarchical reference data.
23. Status cannot be modified through a generic patch.
24. Publishing, cancelling, unpublishing, and completing are dedicated domain operations.
25. Public and management DTOs are separate.
26. The Event module must be ready to support future ticket, payment, reporting, and scanning modules without embedding those modules inside the Event entity.

---

# 41. AI implementation instructions

When implementing this specification, the coding agent must:

1. inspect existing Event, Organization, Payment, Security, and audit-related code before changing files;
2. preserve already implemented behavior unless this specification explicitly replaces it;
3. reuse existing validator architecture;
4. create Flyway migrations and never rely on Hibernate schema auto-update;
5. avoid returning entities from controllers;
6. keep authorization in a dedicated service;
7. write unit, repository, integration, and security tests;
8. update OpenAPI documentation;
9. use RFC 7807 errors;
10. add audit entries in the same transaction as the business change;
11. use optimistic locking;
12. prevent arbitrary organizer/provider assignment;
13. sanitize HTML on the server;
14. keep public responses free of internal and sensitive fields;
15. emit domain events for cross-module side effects;
16. document any deviation from this specification in the implementation pull request;
17. avoid implementing ticket-domain logic inside Event entities;
18. ask for an explicit product decision only when a genuine contradiction cannot be resolved from existing code and this document.

---

# 42. Definition of done

The Event feature is complete when:

- an organizer can create unlimited draft events;
- each event receives UUID, public ID, and unique slug;
- free and paid events are modeled explicitly;
- paid-event publication requires company and approved provider;
- provider and currency compatibility are enforced server-side;
- event supports in-person, online, and hybrid modes;
- location, contact, event type, tags, rich description, capacity, and properties are persisted;
- multiple event collaborators are supported;
- event-level permissions are enforced;
- publish, unpublish, cancel, and complete transitions are implemented;
- publication readiness returns structured violations;
- public and private visibility behavior is correct;
- every material change is audited with actor and before/after values;
- concurrency conflicts do not silently overwrite data;
- public API exposes only safe published data;
- Flyway migrations are present;
- OpenAPI is updated;
- automated tests cover happy paths, validation, authorization, lifecycle, security, and concurrency.
