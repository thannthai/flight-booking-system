# Flight Booking System — Agent Guide

## Working rules

- Read `git status`, recent commits, relevant project documentation and source/tests before continuing work. If a local handoff is available, verify it against the source; earlier AI reports are not proof of correctness.
- Preserve existing local changes. Make focused changes within the requested scope; audit-only means no implementation. Confirm material changes to business rules, API contracts or architecture before implementing them.
- Follow existing module boundaries and conventions. Explain trade-offs; avoid framework rewrites, new patterns or dependencies without a concrete need.
- Communicate in Vietnamese; keep code, filenames and technical identifiers in English.

## Repository map

| Path | Responsibility |
| --- | --- |
| `backend/src/main/java/com/thanh/backend/` | Java 21 / Spring Boot modular monolith: `auth`, `airport`, `airline`, `aircraft`, `flight`, `booking`, `payment`, `notification`, `common`. Controllers handle transport, services own business decisions, repositories handle persistence. Uses Spring Security/JPA, PostgreSQL, Redis and payOS. |
| `backend/src/main/resources/` | `application.yaml` configuration, `db/migration/` Flyway scripts, demo-flight templates. |
| `frontend/` | Next.js App Router, React, TypeScript and Tailwind. `app/` routes/composition; `components/` domain UI/controllers; `lib/` API/auth coordination, workflow models and helpers; `types/api.ts` API DTOs; `tests/` Node/jsdom; `e2e/` Playwright. |
| `.github/workflows/` | Separate backend and frontend verification. Deployment packaging: `backend/Dockerfile`, `frontend/vercel.json`; local services: `docker-compose.yml`. |
| `docs/` | Project specification, system architecture (C4 levels 1–3), database design, critical flows and `adr/` decision records. Verify contracts and business rules against controllers, DTOs, enums and migrations. |

Use `backend/pom.xml` and `frontend/package.json` for actual versions. Read relevant installed Next.js docs in `frontend/node_modules/next/dist/docs/` before frontend changes. `frontend/AGENTS.md` is maintained by Next.js.

## Invariants to preserve

- **Backend is authoritative** for Booking, Seat Hold, Payment and Authorization. Enforce ownership/admin access there; frontend state, redirects and query parameters cannot establish payment success or seat ownership.
- Never double-book a seat. Preserve Redis lease ownership, expiry and generation fencing together with persisted database state. Cleanup must not release a newer lease; preserve transaction/rollback/after-commit behavior and lock ordering.
- Booking creation must be atomic across legs, passengers, tickets and seats. Backend payment deadlines cannot be extended by client timers, late schedulers or callbacks.
- Payment creation and verified webhooks remain idempotent: duplicate/late events must not issue duplicate tickets or reopen terminal bookings. Never hold database locks across payment-provider network calls.
- Preserve auth single-flight refresh, stale-response fencing, logout and account-bound draft cleanup. Access tokens stay in memory; refresh uses the backend HttpOnly cookie. Never automatically retry createBooking after an unknown request outcome.
- Tests must not contact the real payment provider or production databases. Mock the gateway and use isolated test data/services.

## Commands and verification

Backend commands run from `backend/`, with JDK 21:

```sh
./mvnw test          # Surefire: *Test; excludes *IT
./mvnw clean verify  # Unit + Failsafe *IT + packaging; same as backend CI
```

Integration tests extend `AbstractIntegrationTest` and start PostgreSQL/Redis Testcontainers; Docker must be available. For locks, Redis leases, rollback and concurrency, use real services, independent transactions, bounded coordination and persisted-state assertions. Mock interactions alone do not prove concurrency. The Dockerfile skips tests; building the image is not verification.

`./mvnw clean verify` also runs a JaCoCo coverage gate (unit + IT combined): `booking.service` and `payment.service.impl` packages and the whole backend must stay above the thresholds in `pom.xml`. Rule `includes` use dotted package names. Raise thresholds as tests grow; never lower them to pass. `.github/workflows/secret-scan.yml` runs gitleaks over the full history.

`ArchitectureTest` (ArchUnit, plain unit test) locks the module dependency graph and layer rules. It permits only today's module edges, including the known booking/payment and flight/reference-data cycles; a new cross-module dependency fails it, so justify the edge and update the allowed map deliberately rather than loosening a rule.

Frontend commands run from `frontend/`. Use Node 24 as in frontend CI, pnpm pinned by `packageManager`, and only `pnpm-lock.yaml`:

```sh
pnpm install --frozen-lockfile
pnpm test
BACKEND_API_URL=https://backend.example.invalid/api/v1 pnpm exec next typegen --webpack
pnpm typecheck
pnpm lint
pnpm audit --prod
BACKEND_API_URL=https://backend.example.invalid/api/v1 pnpm build
pnpm test:e2e
```

- The non-live HTTPS URL is for checks with no backend calls; configure a real local/test upstream for interactive use. Typegen loads production configuration and must precede standalone typecheck on a fresh checkout. Do not run typecheck while build regenerates `.next/types`.
- Build intentionally uses Webpack. Browser smoke requires that build and an installed Chromium, or `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. Current Playwright tests mock APIs; Node/jsdom tests are not browser E2E, and neither proves backend/provider integration.
- Run relevant regression/negative/authorization tests, then broader checks as warranted. Never weaken assertions or disable checks to obtain PASS. Report exact commands, counts, warnings, blockers and NOT RUN/NOT VERIFIED items.

## Configuration, migrations and handoff

- Add versioned Flyway migrations in `backend/src/main/resources/db/migration/`; do not edit applied migrations or reset schema history to bypass failures. Preserve constraints/data compatibility and JPA `ddl-auto=validate`. `MigrationUpgradeIT` upgrades a database seeded at V10 to the latest schema; when adding a migration, extend it to cover the new constraints and any data backfill.
- Rate limits (`app.rate-limit`) key on the client address: behind any reverse proxy set `RATE_LIMIT_TRUSTED_PROXY_HOPS` to the trusted proxy count and verify the forwarded chain on the deployed domain, or all customers share one counter.
- Backend environment keys come from `application.yaml`; do not assume `.env` is loaded automatically. Frontend template: `frontend/environment.example` → ignored `.env.local`. Browser calls `/api/v1`; server-only `BACKEND_API_URL` ends in `/api/v1` and requires HTTPS in production. Check cookie/CORS/callback configuration before deployment; repository config does not prove hosting settings.
- Never commit/log credentials, tokens, private keys, real payment details or sensitive passenger data. Keep `.env*`, `backend/target/`, `node_modules/`, `.next/`, `next-env.d.ts`, `*.tsbuildinfo` and browser artifacts ignored. Generate Next types rather than editing generated declarations.
- For multi-session work, leave a concise local handoff with verified milestones, open issues and commands; keep transient progress/test counts out of this guide. Ask before destructive operations or production-data changes; commit/push/deploy only when requested.

## UI rules (frontend/)
- Sau mỗi thay đổi UI: chạy dev server, chụp screenshot bằng Playwright ở 375/768/1280px, tự kiểm tra rồi mới báo xong.
- Tối đa 2 font, 1 accent color. Không gradient tím/xanh, không emoji làm icon, không card lồng card.
- Spacing theo scale 4/8px. Không dùng shadcn/Tailwind mặc định mà không chỉnh token.
- Phải có đủ trạng thái loading / empty / error cho mọi màn hình có data.
- Không đổi logic, props, API call khi chỉnh UI. Không thêm dependency mà không báo.
- Trước khi kết thúc, liệt kê những gì đã sửa và những gì còn lại.