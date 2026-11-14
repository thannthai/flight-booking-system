# Flight Booking System — System Architecture

This document covers component responsibilities, integration and design trade-offs. See [Project Specification](01-project-specification.md) for functionality.

## 1. Architecture overview

Flight Booking System uses a **Spring Boot modular monolith** behind a Next.js customer frontend. Backend packages organize authentication, catalog, flight inventory, booking and payment responsibilities. They run within one Spring application and share a PostgreSQL database and transaction manager.

Modules communicate through Java calls and shared repositories. Next.js and Spring Boot are deployed separately.

The main architectural goals are:

- Keep inventory, reservation, payment and authorization decisions authoritative in the backend.
- Coordinate concurrent seat allocation without partially saved reservations.
- Separate external payment activity from local database transactions.
- Make reloads and asynchronous confirmation recoverable through persisted state.
- Keep the system understandable and testable at portfolio scale.

Shared persistence supports local transactions, but couples core modules to the same runtime and release boundary.

## 2. System architecture

The diagrams follow the [C4 model](https://c4model.com): level 1 shows the system in its environment, level 2 the deployable containers, and level 3 (section 3) the backend modules. Class-level detail is left to the source.

### 2.1 Level 1: System context

```mermaid
flowchart LR
    Customer(["Customer<br/>Searches, books and pays"])
    Admin(["Administrator<br/>Maintains catalog and inventory"])
    System["Flight Booking System<br/>Search, seat holds, bookings, payments"]
    PayOS["payOS<br/>Payment provider"]
    OpenFreeMap["OpenFreeMap<br/>Map style and tiles"]

    Customer -->|Uses in a browser| System
    Admin -->|Uses admin APIs| System
    System -->|Creates, queries and cancels payment links| PayOS
    PayOS -->|Sends signed payment webhooks| System
    Customer -->|Optional hosted checkout| PayOS
    Customer -->|Loads map style and tiles| OpenFreeMap
```

### 2.2 Level 2: Containers

```mermaid
flowchart LR
    Browser["Customer / browser"]
    Next["Next.js frontend<br/>App Router and API proxy"]
    Backend["Spring Boot backend (Java 21)<br/>REST APIs and business logic"]
    PG[(PostgreSQL)]
    Redis[(Redis)]
    PayOS["payOS"]
    OpenFreeMap["OpenFreeMap"]

    Browser <-->|Pages and same-origin API requests| Next
    Next <-->|REST /api/v1| Backend
    Backend <-->|Persistent business records| PG
    Backend <-->|Seat leases and auth state| Redis
    Backend -->|Create, query or cancel payment link| PayOS
    PayOS -->|Webhook verified by backend| Backend
    Browser -->|Public map style and tiles| OpenFreeMap
    Browser -->|Optional hosted checkout| PayOS
    PayOS -.->|Browser return or cancel navigation| Browser
```

Next.js serves browser components and forwards API requests through a rewrite.

Search, holds, booking creation and payment initialization use HTTP request/response. Webhooks confirm payment independently of the browser; scheduled jobs and listeners run inside Spring Boot.

The browser does not access PostgreSQL or Redis. It can open payOS checkout, but the backend controls the payable amount and interprets the verified payment notification.

## 3. Backend module architecture

### 3.1 Responsibilities

| Module | Responsibility |
| --- | --- |
| `auth` | Accounts, credentials, JWT validation, refresh/revocation state, authenticated principals and Spring Security configuration. |
| `airport` | Airport metadata, including timezones; catalog lookup and administrative maintenance. |
| `airline` | Airline metadata and administrative maintenance. |
| `aircraft` | Aircraft ownership, registrations and cabin capacity configuration. |
| `flight` | Flight search/details, seat maps and initial seat inventory, flight administration and optional demo generation. |
| `booking` | Seat holds, passenger directory, booking/leg/passenger/ticket records, pricing and pending-booking cancellation. |
| `payment` | Payment intents, payOS adapter/configuration, verified webhook processing, cancellation coordination, the payment-success event and unpaid-booking timeout cleanup. |
| `notification` | Notification entities, status/type definitions and persistence. No workflow writes them yet; booking confirmation is shown in the web app. |
| `common` | API/page envelopes, error handling, shared entity metadata, JPA timestamp auditing, cookie utilities and production environment validation. |

### 3.2 Level 3: Module components and dependencies

```mermaid
%%{init: {"flowchart": {"curve": "linear", "nodeSpacing": 40, "rankSpacing": 60, "padding": 20, "subGraphTitleMargin": {"top": 12, "bottom": 30}}}}%%
flowchart TB
    subgraph Boot["Single Spring Boot deployment boundary"]
        direction TB
        Booking["booking"]
        Payment["payment"]
        Flight["flight"]
        Auth["auth<br/>Identity and security"]
        Notification["notification<br/>Records and persistence"]

        Catalog["Catalog support<br/>airport · aircraft · airline"]
        Common["common<br/>Shared infrastructure"]

        Booking -->|cancel payment, payment provider| Payment
        Payment -->|booking and ticket updates| Booking
        Booking -->|flight and seat access| Flight
        Payment -->|seat release| Flight
        Booking -->|user context| Auth
        Payment -->|user context| Auth
        Booking -->|airport and airline via flights| Catalog
        Notification -->|booking and user references| Booking
        Flight -->|seat holder reference| Auth
        Flight --> Catalog
        Catalog -->|deletion checks| Flight
        %% Layout only: shared infrastructure sits beside catalog support.
        Flight ~~~ Common
    end

    classDef core fill:#e8f0fe,stroke:#365b9d,stroke-width:2px,color:#172b4d,font-weight:bold
    classDef support fill:#f8fafc,stroke:#94a3b8,color:#334155
    class Booking,Payment,Flight core
    class Auth,Notification,Catalog,Common support
```

Arrows show source-level dependencies; every module may also use `common`, and `auth` depends on nothing else. Catalog support groups `airport`, `aircraft` and `airline` for readability (`aircraft` also uses `airline`).

This graph is enforced: [`ArchitectureTest`](../backend/src/test/java/com/thanh/backend/ArchitectureTest.java) fails when a module gains a dependency that is not listed, including the existing booking/payment and flight/catalog cycles.

Booking cancellation accesses payment persistence and the gateway. Payment confirmation/cancellation accesses booking, ticket and seat persistence. Flight services read catalog repositories, and catalog deletion checks flight references. These shared entities and repositories simplify transactions but would need separation before extracting independent services.

### 3.3 Request handling pattern

Controllers handle HTTP mapping, request validation, authenticated principals and response DTOs. Services coordinate business validation, ownership and state changes. Repositories read/write records through Spring Data JPA, with selected explicit SQL/JDBC operations where database behavior matters. Mappers separate persistence entities from API responses.

Security filters authenticate protected requests before controller handling; services still enforce resource ownership. Module boundaries are maintained through package conventions and shared code rather than strict domain isolation.

**Sources:** [backend packages](../backend/src/main/java/com/thanh/backend/), [BookingService](../backend/src/main/java/com/thanh/backend/booking/service/BookingService.java), [PaymentServiceImpl](../backend/src/main/java/com/thanh/backend/payment/service/impl/PaymentServiceImpl.java), [FlightService](../backend/src/main/java/com/thanh/backend/flight/service/FlightService.java).

## 4. Data storage and consistency

### 4.1 PostgreSQL and schema ownership

PostgreSQL holds persistent business state: users, catalog/inventory, bookings and legs, passengers/documents, tickets/add-ons, payments. Booking/payment status returned to customers comes from these records, not browser state or provider redirect parameters.

Flyway owns versioned schema changes. The complete migration sequence matters: later migrations refine payment states, uniqueness and flight provenance. Hibernate validates entity/schema compatibility rather than generating the schema. Foreign keys and selected unique constraints complement service validation; they do not replace every business check.

All modules share a database and persistence model. Booking creation can therefore save its related records and seat claims in one local database transaction. Eligible payment confirmation likewise updates payment, booking and tickets together.

### 4.2 Redis has distinct operational roles

| Redis state | Role | Relationship to PostgreSQL |
| --- | --- | --- |
| Seat leases | Temporary, expiring ownership of a selected seat. | Reservation creation also validates persisted ownership, expiry and seat state. Redis alone cannot establish a booked seat. |
| Refresh-token registrations | Track valid refresh credentials and their revocation/rotation. | User identity and roles remain persisted in PostgreSQL. |
| Access-token blacklist | Revoke supplied access tokens before their natural expiry. | Protected-request authentication checks revocation in addition to JWT validity and user lookup. |

Redis is not merely an optional read cache. It participates in admission to booking and authentication; outages can prevent those operations even when PostgreSQL is available.

### 4.3 Transaction boundaries

PostgreSQL and Redis do not share a distributed transaction. The application coordinates leases with persisted seat state, transaction completion and ownership-aware cleanup. Database booking creation is atomic, but the two stores are not claimed to change atomically together. Temporary stale leases/held records are possible; they do not independently authorize a second booking.

Payment-link creation separates a committed local intent, the external payOS call and subsequent URL/QR attachment. This avoids holding database locks across provider network latency, at the cost of intermediate states requiring reconciliation. A failed or timed-out HTTP response does not establish whether a mutation committed.

Concurrency and recovery behavior are exercised by the [backend integration tests](../backend/src/test/java/com/thanh/backend/booking/service/).

**Sources:** [migrations](../backend/src/main/resources/db/migration/), [application configuration](../backend/src/main/resources/application.yaml), [lease coordination](../backend/src/main/java/com/thanh/backend/booking/service/SeatHoldTransactionLifecycle.java), [JWT state management](../backend/src/main/java/com/thanh/backend/auth/service/JwtService.java).

## 5. Communication and integration

### 5.1 Frontend and API proxy

The App Router supplies the main booking page and `/payment/[result]`. The main page composes search, passenger entry, seat selection and payment steps. Domain components own forms/interactions; shared checkout models/builders map data to API DTOs.

The browser API facade delegates HTTP transport, session coordination and typed domain methods to separate helpers. Browser requests target same-origin `/api/v1`; the rewrite forwards them to server-only `BACKEND_API_URL`. The proxy does not calculate authoritative fares, decide ownership or confirm payment.

MapLibre loads OpenFreeMap styles/tiles in the browser. The route is drawn locally from airport coordinates, with a static coordinate fallback. Map loading has bounded retries and online recovery.

This arrangement keeps refresh-cookie requests first-party to the browser. The backend still has explicit CORS/security rules; actual cookie forwarding and secure-cookie behavior depend on hosting configuration and require integration verification.

### 5.2 Authentication and session coordination

Register/login return an access token and user while setting the refresh token through `Set-Cookie`. The frontend keeps the access token in memory and sends it as Bearer authorization; refresh uses the HttpOnly cookie. The backend validates JWT type/expiry/revocation and loads the current user/authorities for protected requests.

Refresh verifies token type/expiry and atomically deletes its Redis registration; only the successful consumer receives replacement tokens. Token-family-wide replay revocation is not implemented.

The session coordinator shares refresh requests within a tab and discards responses from obsolete sessions. In supported browsers on HTTPS/localhost, a same-origin Web Lock serializes refresh, login and logout cookie mutations across tabs. Without Web Locks, per-tab coordination remains; cross-tab serialization is not guaranteed. Logout/account changes clear private drafts. Reload recovery uses tab-local storage; uncertain booking outcomes require reconciliation through history rather than automatic resubmission.

**Sources:** [JWT rotation](../backend/src/main/java/com/thanh/backend/auth/service/JwtService.java), [route map](../frontend/components/maps/FlightRouteMap.tsx), [Next rewrite](../frontend/next.config.mjs), [API facade](../frontend/lib/api.ts), [session coordination](../frontend/lib/auth-session.ts), [security configuration](../backend/src/main/java/com/thanh/backend/auth/security/SecurityConfig.java), [cookie handling](../backend/src/main/java/com/thanh/backend/common/utils/CookieUtils.java).

### 5.3 payOS and payment observation

The payment module calls payOS through a gateway interface backed by the provider SDK. It creates/reuses checkout links, can query a link during initialization reconciliation, submits asynchronous link cancellation and verifies webhook signatures.

Marked demo accounts use an authenticated simulated-confirmation endpoint, while ordinary accounts retain payOS. Both use persisted booking/payment state and the existing deadline; the simulation does not bypass ownership or reopen terminal bookings. The public demo entry uses one shared account.

The backend receives a webhook and verifies it **before** applying payment rules. Eligible receipts update persisted payment/booking/ticket state; duplicate or terminal receipts follow the existing consistency protections. A browser redirect has no authority to perform these transitions.

The frontend renders the returned QR payload locally and periodically reads payment status plus booking details. Those status reads query application persistence, not payOS on every poll. Return/cancel pages resume this same authenticated status-reading flow.

### 5.4 Events, listeners and jobs

| Mechanism | Implemented interaction |
| --- | --- |
| Payment success event | Published in-process after the payment transaction commits. No listener is registered today, so it sends no email or notification; customers see the confirmation in the web app. |
| Provider link cancellation | Submitted through an asynchronous gateway method; it does not itself refund money. |
| Unpaid-booking cleanup | A scheduled job in `payment` processes overdue pending bookings, updates local state/releases seats, then requests provider cancellation when applicable. |
| Optional demo generation | A startup/scheduled process in `flight` generates simulated inventory through a transactional writer, subject to environment/target checks and inventory budgets. |
| Lease cleanup | Internal deferred cleanup work in `booking`; it is not a notification/event-delivery queue. |

A fresh database seeds airports but no flight schedules. An existing `ROLE_ADMIN` account can provision inventory through admin APIs; registration creates `ROLE_USER`. The optional demo generator requires configured airlines/airports and explicit local/demo opt-in. Its routes are in [demo-flight-templates.yaml](../backend/src/main/resources/demo-flight-templates.yaml), with activation and database allowlist settings in [application.yaml](../backend/src/main/resources/application.yaml).

These events and jobs run inside Spring Boot. In-process asynchronous execution is not a durable message queue; follow-up delivery is best-effort and separate from the committed booking/payment outcome.

**Sources:** [gateway adapter](../backend/src/main/java/com/thanh/backend/payment/service/impl/PayOSGatewayServiceImpl.java), [success event](../backend/src/main/java/com/thanh/backend/payment/event/PaymentSuccessEvent.java), [timeout job](../backend/src/main/java/com/thanh/backend/payment/scheduler/BookingPaymentCleanupScheduler.java), [demo generator](../backend/src/main/java/com/thanh/backend/flight/demo/DemoFlightGenerator.java).

### 5.5 Deployment configuration boundary

The [production environment validator](../backend/src/main/java/com/thanh/backend/common/config/ProductionEnvironmentValidator.java) checks required credentials and HTTPS settings before datasource initialization.

For the portfolio on Render + Vercel, keep the backend's `demo`/`dev` Spring profile when using Try demo. Set `COOKIE_SECURE=true` on the HTTPS-hosted backend. Vercel's Production environment does not select the backend Spring profile.

For a non-demo backend, activate `prod` or `production` and supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `PAYOS_RETURN_URL` and `PAYOS_CANCEL_URL`. Missing values stop startup. These profiles require Secure cookies and turn off SQL logging and the Swagger UI/OpenAPI endpoints by default; set `SWAGGER_ENABLED=true` only if public API documentation is intended.

CORS values must be exact HTTPS origins without paths or trailing slashes; payOS callback URLs must also use HTTPS. Set the frontend's server-only `BACKEND_API_URL=https://<backend-host>/api/v1` before building.

After deployment, verify login, reload, refresh and logout on the HTTPS domain. Automated tests do not verify hosting TLS or proxy cookie forwarding.

## 6. High-level booking workflow

The sequence shows a successful new booking. The frontend participant groups browser UI and the Next.js rewrite for readability; every frontend-to-backend API request uses that proxy. Payment setup may reuse existing state, and polling can occur before or during webhook processing.

```mermaid
sequenceDiagram
    actor User as Customer
    participant FE as Next.js frontend
    participant API as Spring Boot backend
    participant DB as PostgreSQL
    participant R as Redis
    participant P as payOS

    User->>FE: Search and choose flight(s)
    FE->>API: Search flights and read seat maps
    API->>DB: Read flight inventory
    DB-->>API: Flights and seats
    API-->>FE: Search results and seat maps
    Note over User,FE: Enter passenger details and select seats#59; sign in before hold
    FE->>API: Request seat hold
    API->>R: Acquire or reuse owned seat leases
    API->>DB: Check and persist seat holds
    API-->>FE: Hold expiry and selected seats

    User->>FE: Confirm Booking
    FE->>API: Create booking
    API->>R: Validate active owned holds
    API->>DB: Atomically save booking, passengers, pending tickets and seat claims
    DB-->>API: Commit
    API-->>FE: Booking ID, PNR and final total

    FE->>API: Request payment link for booking
    API->>DB: Prepare local payment intent
    API->>P: Create checkout link outside DB transaction
    P-->>API: Checkout URL and QR payload
    API->>DB: Attach link data if still eligible
    API-->>FE: Payment details
    FE-->>User: Display QR and optional checkout link
    User->>P: Complete external payment

    par Optional browser navigation
        P-->>FE: Browser returns to payment result page
        Note over User,FE: Redirect alone does not confirm payment
    and Independent webhook delivery
        P->>API: Send payment webhook
        API->>API: Verify signature and payment eligibility
        API->>DB: Confirm payment and booking#59; issue tickets atomically
        DB-->>API: Commit
        API-->>P: Acknowledge webhook
    end

    FE->>API: Query payment status and booking details
    API->>DB: Read persisted status and tickets
    API-->>FE: Confirmed booking and issued tickets
    FE-->>User: Display confirmation and e-tickets
```

The critical handoff is from a pending reservation to backend-confirmed issuance. The initial booking already owns its allocated seats but does not yet have issued tickets. A valid webhook changes that state; polling exposes the committed result to the customer. Cancellation/expiry and refund-review branches are defined in the specification rather than expanded into this success-path diagram. [Critical Flows](05-critical-flows.md) details the hold, webhook and expiry sequences.

**Sources:** [booking orchestration](../backend/src/main/java/com/thanh/backend/booking/service/BookingService.java), [payment orchestration](../backend/src/main/java/com/thanh/backend/payment/service/impl/PaymentServiceImpl.java), [frontend checkout composition](../frontend/app/page.tsx), [frontend payment recovery](../frontend/lib/payment.ts).

## 7. Architecture decisions and trade-offs

The table summarizes the main choices; the [decision records](adr/README.md) give the context, consequences and evidence for the most important ones.

| Decision | Benefit for this project | Cost or boundary |
| --- | --- | --- |
| Modular monolith | One backend codebase and local transactions keep reservation consistency manageable. | Shared persistence and direct cross-module access limit isolation and independent releases. |
| PostgreSQL plus Redis | Durable relational state combines with expiring holds and revocable sessions. | Coordinating two stores requires explicit failure handling; Redis is an operational dependency. |
| Backend-authoritative workflow | Ownership, price and payment results do not depend on browser trust. | Frontend must refresh authoritative state and handle stale drafts/unknown outcomes. |
| Webhook confirmation | Payment settlement is observed independently of whether the browser returns. | Confirmation depends on provider delivery/verification; the customer may wait while polling. |
| Separate payment intent and provider call | Slow external HTTP does not hold the preparation transaction open. | Partial initialization is a valid intermediate condition requiring reuse/reconciliation. |
| Same-origin frontend proxy | A consistent browser API path supports cookie-based refresh. | Adds a forwarding boundary whose configuration and cookie behavior need integration testing. |
| In-process asynchronous work | Simple follow-up processing without operating a message broker. | No durable delivery, replay or retry guarantee for notifications/provider follow-up. |
