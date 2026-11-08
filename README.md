# Flight Booking System

Java backend engineering through a complete flight booking workflow.

![Java 21](https://img.shields.io/badge/Java-21-334155?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot 3.5](https://img.shields.io/badge/Spring_Boot-3.5-334155?style=flat-square&logo=springboot&logoColor=white)
![Next.js 16](https://img.shields.io/badge/Next.js-16-334155?style=flat-square&logo=nextdotjs&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-334155?style=flat-square&logo=postgresql&logoColor=white)
![Redis 7](https://img.shields.io/badge/Redis-7-334155?style=flat-square&logo=redis&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-334155?style=flat-square&logo=typescript&logoColor=white)

**[Live Demo ↗](https://skywaybooking.vercel.app/)** · [Technical Highlights](#technical-highlights) · [Run Locally](#run-locally) · [Documentation](#documentation)

A full-stack flight booking application for searching flights, selecting seats, managing passengers and completing checkout through payOS. Built with a Spring Boot backend and Next.js frontend, it focuses on consistent reservations under concurrent requests and asynchronous payment confirmation.

Flights marked `DEMO` are simulated inventory. Printed e-tickets are demo booking confirmations, not airline boarding passes.

## Features

- **Flight discovery:** domestic/international airport data, one-way and round-trip search, airline/fare/time filters and airport-local departure/arrival times.
- **Seat selection:** economy/business seat maps, auto-assignment and temporary holds after sign-in.
- **Passenger management:** adult/child/infant validation, guardian selection, saved passenger quickfill and optional baggage, meal and insurance add-ons.
- **Booking and payment:** backend-calculated totals, booking references, payOS QR/checkout links and status recovery after reload.
- **Personal bookings:** history, payment continuation, pending-booking cancellation and printable confirmed tickets.
- **Authentication:** registration, login, refresh and logout with account-bound checkout cleanup.
- **Administration APIs:** airport, airline, aircraft and flight management.

Confirmation notifications are recorded in the database; email delivery is not implemented. Voucher application and refund execution remain planned workflows.

## Technical Highlights

- **Concurrency-safe allocation:** Redis leases work with persisted ownership, expiry checks and conditional seat updates to protect competing reservations.
- **Fixed hold deadlines:** retries reuse a live hold without extending its expiry; stale cleanup must not release a newer hold.
- **Transaction consistency:** booking records, legs, passengers, tickets and seat claims commit together. Provider network calls run outside payment preparation/attachment transactions.
- **Idempotent payment confirmation:** verified receipts are checked against the payable amount and deadline; duplicate callbacks do not issue tickets again, and late receipts cannot reopen cancelled bookings.
- **Session lifecycle:** memory-held access tokens, HttpOnly refresh cookies and stale-response checks protect logout and account switching.
- **Recovery after uncertain requests:** checkout avoids automatically resending a booking request whose previous outcome is unknown.

## Tech Stack

| Area | Technologies and purpose |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA and Bean Validation. |
| Frontend | Next.js 16 App Router, React 19, TypeScript and Tailwind CSS. |
| Persistence | PostgreSQL with Flyway migrations for durable business records and schema evolution. |
| Temporary state | Redis 7 for seat leases and authentication-related state. |
| Payments | payOS SDK, QR checkout and verified webhooks. |
| Testing | JUnit, Mockito and PostgreSQL/Redis Testcontainers; Node/jsdom regressions and Playwright browser smoke. |
| Tooling | Maven Wrapper, pnpm, Docker Compose and GitHub Actions. |

## Architecture

```mermaid
flowchart LR
    Browser["Browser"]
    Frontend["Next.js frontend<br/>UI and API proxy"]
    Backend["Spring Boot<br/>Modular monolith"]
    DB[(PostgreSQL)]
    Redis[(Redis)]
    PayOS["payOS"]

    Browser <-->|Pages and API requests| Frontend
    Frontend <-->|REST /api/v1| Backend
    Backend <--> DB
    Backend <--> Redis
    Backend -->|Checkout link| PayOS
    PayOS -->|Webhook verified by backend| Backend
```

The backend modules share one application and database. Browser API calls use a same-origin Next.js rewrite; booking, pricing and authorization decisions remain in Spring Boot. The frontend polls persisted payment status, so a browser redirect alone never confirms payment.

## Run Locally

### Prerequisites

- JDK 21.
- Node.js 24 and pnpm 12.3.4, matching frontend CI.
- Docker with Docker Compose for PostgreSQL 17 and Redis 7.
- OpenSSL for the example local JWT-key command, and your payOS development integration credentials.

### 1. Start local services

```sh
git clone https://github.com/thannthai/flight-booking-system.git
cd flight-booking-system
docker compose up -d postgres redis
```

Wait for PostgreSQL and Redis to finish starting. The backend defaults match [docker-compose.yml](docker-compose.yml): PostgreSQL database `flight_booking` on port 5432 and Redis on port 6379. Use `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `SPRING_DATA_REDIS_*` to override them.

### 2. Start the backend

In a terminal at the repository root, replace the payOS placeholders with your development values:

```sh
cd backend
export JWT_SECRET="$(openssl rand -base64 32)"
export PAYOS_CLIENT_ID='your-development-client-id'
export PAYOS_API_KEY='your-development-api-key'
export PAYOS_CHECKSUM_KEY='your-development-checksum-key'
export PAYOS_RETURN_URL='http://localhost:3000/payment/success'
export PAYOS_CANCEL_URL='http://localhost:3000/payment/cancel'
./mvnw spring-boot:run
```

The API runs at `http://localhost:8080`; Flyway applies migrations at startup. Spring Boot reads exported variables and does not automatically load `backend/.env`. Keep credentials out of Git. Payment confirmation requires a configured, publicly reachable webhook; automated payment tests mock the gateway.

### 3. Start the frontend

In another terminal at the repository root:

```sh
npm install --global pnpm@12.3.4
cd frontend
pnpm install --frozen-lockfile
test -f .env.local || cp environment.example .env.local
pnpm dev
```

Open **http://localhost:3000**. The template keeps browser calls at `/api/v1` and sets server-only `BACKEND_API_URL=http://localhost:8080/api/v1`.

Fresh migrations seed airports, not flight schedules. Inventory must be provisioned through admin APIs using an existing `ROLE_ADMIN` account; registration creates `ROLE_USER`. The optional demo generator requires existing configured airlines and explicit local/demo opt-in; its templates are in [demo-flight-templates.yaml](backend/src/main/resources/demo-flight-templates.yaml) and its activation settings in [application.yaml](backend/src/main/resources/application.yaml).

## API Documentation

After starting the backend:

- [Swagger UI](http://localhost:8080/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)

These are local development endpoints. Protected APIs require a Bearer access token; administrative APIs require `ROLE_ADMIN`.

## Testing

From `backend/`, with Docker available for integration tests:

```sh
./mvnw clean verify
```

From `frontend/`:

```sh
pnpm test
BACKEND_API_URL=https://backend.example.invalid/api/v1 pnpm exec next typegen --webpack
pnpm typecheck
pnpm lint
pnpm audit --prod
BACKEND_API_URL=https://backend.example.invalid/api/v1 pnpm build
```

The HTTPS placeholder is for build/type-generation checks without backend calls. Build intentionally uses Webpack. After building, browser smoke can run with:

```sh
pnpm exec playwright install chromium
pnpm test:e2e
```

Integration tests use isolated services; browser smoke uses mocked APIs. Neither requires real money transfers or production data.

## Documentation

- [Project Specification](docs/01-project-specification.md) — features, scope and key business rules.
- [System Architecture](docs/02-system-architecture.md) — module boundaries, integrations and design decisions.
- [Database Design](docs/03-database-design.md) — tables, relationships and integrity protections.
- [Portfolio Demo](docs/04-demo-experience.md) — isolated demo configuration, public accounts and simulated checkout.
