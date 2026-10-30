# Project Status — Flight Booking System

Cập nhật: **2026-10-30 +07:00**. Branch `main`; baseline trước đợt commit này là `4a2fe48`. Checkpoint dưới ghi verification ngay trước commit/push được người dùng yêu cầu; dùng Git log và upstream để xác nhận commit đã publish. Các checkpoint cũ là lịch sử, không phải trạng thái hiện tại.


## Vercel install failure investigation — 2026-10-30

- User-provided Vercel Build Logs for published commit `b7a4168` confirm `ERR_PNPM_IGNORED_BUILDS` for `unrs-resolver@1.12.2` after successful lockfile verification/downloads. This matches the clean local reproduction below; failure occurs during install, before the application build.
- Reproduced a concrete clean-install failure locally using Node24.3.0 / pnpm12.3.4, copied manifests, empty node_modules/store/cache and CI=true: packages downloaded and lockfile passed supply-chain policies, then `ERR_PNPM_IGNORED_BUILDS` for `unrs-resolver@1.12.2`. The earlier pre-commit install reused existing local dependencies/cache and did not expose this first-install approval requirement.
- Inspected the resolver postinstall/native-binding preparation and added only `allowBuilds: { unrs-resolver: true }` to frontend/pnpm-workspace.yaml. Package versions, manifest, lockfile and other security policies unchanged. A second empty-node_modules install using downloaded tarballs PASS, postinstall executed, exit0; no blanket script approval or disabled frozen-lockfile.
- Copied tracked frontend source into that isolated install: tests80/80 PASS, typegen/typecheck PASS, lint0 errors/38 warnings, production Webpack build PASS with non-live HTTPS upstream. Logs `/tmp/flight-vercel-install-7nba3z2q/install.log` (before) and `/tmp/flight-vercel-fixed-l4uq8s0z/{install,tests,typegen,typecheck,lint,build}.log` (after). Local macOS verification; Vercel Linux build/deployment after the fix remains NOT VERIFIED. No backend/business/UI changes or real provider/database calls. User authorized a focused commit/push of this fix and status update; Git history/remote are publication evidence, and push may trigger hosting auto-deploy. No manual deployment.

## Final pre-commit verification — 2026-10-30

- Reviewed staged/unstaged/untracked changes against HEAD; source, tests, frontend CI/config, instruction/docs and intended deletions classified. Backend source/API contracts/Flyway migrations unchanged. Full inventory and assessment: `docs/testing/FINAL_PRE_COMMIT_VERIFICATION.md`.
- Backend `./mvnw clean verify` on temporary Temurin21.0.13: **129/129 PASS** = Surefire33 + Failsafe96; failures/errors/skipped0. PostgreSQL/Redis Testcontainers and mocked payment gateway. Maven BUILD SUCCESS and executable JAR packaging verified. Expected negative-path SQL errors are not test failures.
- Pinned pnpm12.3.4 frozen install PASS; typegen/typecheck PASS; frontend tests **80/80 PASS**; lint0 errors/38 warnings; Next16.4.1 production Webpack build PASS; actual Chrome Playwright mock smoke **4/4 PASS,7.6s**. No real backend/provider/database calls from browser tests.
- Production audit0 advisories, exit0. Supplemental full audit exit1 due only1 LOW dev-only deepmerge GHSA-gcqx-w48m-w2h4; no patch in audit response. No suppressions. All mandatory gates PASS; no BLOCKER/HIGH identified in the verified changes.
- Fixed small pre-commit documentation issues: invalid frontend setup/env URL, outdated runtime versions/demo-generator descriptions, unverified demo URL/broken README logo, stale payment API examples and whitespace. Restored missing root CLAUDE.md import hook referenced by AGENTS.md. No application source, tests or dependency graph changes during this final verification.
- Candidate secret/debug/disabled-check scan and manual review found only synthetic test credentials/unsafe-URL rejection fixtures, no real credentials/private customer data. Environment files/build artifacts stay ignored. Two generated files are removed from tracking only; their local copies remain. Root AGENTS paths/commands, CI YAML and package/lock consistency checked.
- Branch/upstream confirmed main→origin/main at thannthai/flight-booking-system; remote tip matches baseline immediately before commit preparation. Explicit allowlist staging, no force/reset/rebase. Push is authorized only after final staged checks; Git log/remote are publication evidence. A push may trigger existing hosting auto-deploy; no manual deploy or production data/payment action is authorized/performed.
- Remaining: real GitHub workflow/hosting outcome, proxy cookies/CORS, configured callback URLs and provider signature/full-stack browser integration NOT VERIFIED; missing layout icon assets LOW; serialized-vs-airport departure calendar limitation MEDIUM; existing lint warnings and dev-only advisory retained. Deployment manual checklist is in FRONTEND_COMPLETION.md and final verification report.
- Local logs `/tmp/flight-precommit/backend-verify.log`, `frontend-{install,tests,typegen,typecheck,lint,build,e2e}.log`, `frontend-audit-{prod,full}.json`; XML reports under ignored backend/target. No log/cache artifacts staged.

## Frontend repository cleanup — Final verification

- Reviewed root/frontend instructions, all frontend source/config/test entry points, static import resolution, dynamic assets and Git ignore/tracking. Full KEEP/REMOVE/IGNORE/REVIEW MANUALLY inventory: `docs/testing/FRONTEND_REPOSITORY_CLEANUP.md`.
- Consolidated shared frontend guidance in root AGENTS.md, added root CLAUDE.md importing it and removed the redundant frontend/CLAUDE.md wrapper. Kept frontend/AGENTS.md because installed Next.js manages/recreates its version-specific block; updated that block using the installed generator, hasCurrentAgentRules=true. No lost project guidance; actual new Claude/Codex session loading NOT RUN.
- Removed unconsumed components/ui/select.tsx and popover.tsx after resolved-import and repository/tooling/test checks. Removed ignored duplicate .env.local.example; canonical environment.example retained. pnpm-workspace.yaml contains active package-manager settings, components.json is shadcn tooling config, vercel.json pins deployment commands: all KEEP, unchanged.
- Added ignore rules and used `git rm --cached` ONLY for frontend/next-env.d.ts and tsconfig.tsbuildinfo. Both files remain local; node_modules/.next/test-results caches preserved. Index initially empty; now ONLY these2 generated-file removals are staged. No commit/push/deploy. Existing unrelated changes remain untouched/unstaged.
- Frontend CI TypeScript step now generates types first (`pnpm exec next typegen --webpack && pnpm typecheck`) so a fresh checkout does not depend on tracked generated declarations. Backend CI unchanged. Fresh-source copy without initial .next/next-env.d.ts, reusing local dependencies: typegen + typecheck PASS. Real GitHub run NOT VERIFIED.
- Required checks: **80/80 frontend tests PASS**, failed/cancelled/skipped0; typecheck PASS; lint0 errors/38 visible warnings; Next16.4.1 production Webpack build PASS; focused whitespace check PASS. Logs `/tmp/flight-frontend-cleanup-{tests,typegen,typecheck,lint,build}.log`, `/tmp/flight-frontend-cleanup-fresh-{typegen,typecheck}.log`. Browser E2E/backend Maven/security audit NOT RUN this cleanup task.
- Remaining source/UI/API/test/public-asset bytes verified unchanged from cleanup-start snapshot, excluding the2 removed unused primitives. package.json, pnpm-lock.yaml, pnpm-workspace.yaml, components.json, environment.example and vercel.json hashes unchanged. No dependency install/update. Airline SVGs KEEP because runtime URLs derive from backend airline codes. Missing layout icon assets/README favicon reference and package-manager policy choices remain REVIEW MANUALLY; no UI/business/architecture change.

## Targeted frontend architecture refactor — Final verification

- Sequential checkpoints: Phase1 checkout contracts74/74 tests + TypeScript PASS → Phase2 PassengerDetails76/76 + TypeScript PASS → Phase3 API/Auth80/80 + TypeScript PASS. All failed/cancelled/skipped0. Final80 includes19 React DOM/jsdom component tests; browser smoke is separate.
- Shared `lib/checkout-model.ts` replaces UI-owned SearchParams and types checkout props/callbacks. Pure `checkout-requests.ts` builders preserve backend payloads; HTML guardian values normalize to number/null before validation, API optional guardian field stays omitted for non-infants. Regression tests exercise actual dropdown0/1, passenger mapping, both legs/original passenger indices and outbound-only add-ons. Removed unused duplicate `types/index.ts` after checking consumers; `types/api.ts` unchanged.
- PassengerDetails now composes controlled ContactFields/PassengerFields/PassengerAddOns and usePassengerForm. Contact/passenger validation and request mapping are testable without React. Existing quickfill, contact copying, directory save, autosave, pricing and flow preserved; added interaction coverage for quickfill/add-ons/remount. Static JSX className/id/placeholder inventory matches pre-refactor screen. No UI redesign, new framework or state library.
- API facade retained. `http.ts` owns stateless HTTP/error/envelope handling; `auth-session.ts` owns isolated lifecycle/refresh/fencing; `domain-api.ts` owns typed named endpoints; `private-session.ts` owns checkout storage keys/cleanup. Auth tests now use createApiClient() instead of deleting facade module cache. New tests cover isolated clients, concurrent protected401 refresh, logout fencing a mutation awaiting refresh, and account switch during response decoding. Added epoch check before401 replay; unknown-outcome network/5xx protections preserved.
- Final lint PASS:0 errors/**38 visible warnings** (previous recorded52; no lint rule changes). Production Next16.4.1 Webpack build PASS. Post-build TypeScript with noUnusedLocals/noUnusedParameters PASS. An earlier extra unused check overlapped build and failed because .next/types were being regenerated; sequential rerun PASS, no source workaround.
- Actual Chrome browser smoke on latest build **4/4 PASS,12.4s**, all API responses mocked and requests outside localhost blocked. No backend/provider/database calls. Hosting cookies/CORS, real webhook/provider and full cross-stack E2E remain NOT VERIFIED. Backend Maven NOT RUN because backend source/contracts/CI unchanged.
- Task-relative changes24 frontend files (12 modified/11 new/1 removed), plus2 documentation files. Existing unrelated changes retained. package.json and pnpm-lock.yaml hashes unchanged from task start; no dependency install/update. Focused frontend diff whitespace check PASS.
- Commands/evidence/file map/remaining limits: `docs/testing/FRONTEND_ARCHITECTURE_REFACTOR.md`. Logs `/tmp/flight-architecture-phase{1,2,3}-{tests,typecheck}.log`, `/tmp/flight-architecture-final-{lint,build,unused,e2e}.log`. No commit/push/deploy.

## Frontend completion — Final verification

- Completed sequential phases: F15 passenger validation → frontend quality gates → actual browser smoke → final verification. Backend source/contracts/migrations and `.github/workflows/ci.yml` unchanged. Full report, changed-file map, commands, remaining severity and deployment checklist: `docs/testing/FRONTEND_COMPLETION.md`.
- Final regression **67/67 PASS**, failures/cancelled/skipped0, including16 React DOM/jsdom component tests and11 passenger-validation tests. Phase1 checkpoint65/65; final2 component tests verify directory document removal rejection and canonical refetch after update.
- TypeScript PASS; additional `tsc --noEmit --incremental false --noUnusedLocals --noUnusedParameters` PASS. Lint exit0 with **0 errors/52 visible warnings**; production `next build --webpack` PASS. Frozen pnpm12.3.4 install (without ignore-scripts) PASS; pnpm-lock.yaml sole lockfile. Frontend workflow YAML parse PASS; real GitHub Actions run NOT VERIFIED.
- Latest actual Playwright/Google Chrome browser run against latest production build: **4/4 PASS,7.4s**. All API responses mocked, all requests outside localhost blocked; no Spring Boot/database/provider calls. No cross-stack or real webhook/provider-signature PASS claimed. Initial sandbox server-listen attempt NOT RUN; approved local execution succeeded.
- Production pnpm audit0 advisories (exit0); full audit1 LOW dev-only `deepmerge` GHSA-gcqx-w48m-w2h4 (exit1), no published patch in audit response. No critical/high/moderate findings in that response.
- Actual final checks used existing pnpm-installed dependencies and npm script runner: `npm --prefix frontend test`, `npm --prefix frontend run typecheck`, `npm --prefix frontend run lint`, `NEXT_TELEMETRY_DISABLED=1 BACKEND_API_URL=https://backend.example.invalid/api/v1 npm --prefix frontend run build`; browser from frontend: `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm run test:e2e`. Logs `/tmp/frontend-completion-final-{tests,typecheck,lint,build}.log`, `/tmp/frontend-completion-e2e-final.log`, `/tmp/frontend-completion-final-{prod,full}-audit.json`.
- Remaining: backend serialized departure calendar vs airport-local date at midnight (MEDIUM contract limitation); hosting cookies/CORS, real payOS and cross-stack flow NOT VERIFIED; legacy lint warnings and unsupported document-only deletion retained explicitly. Frontend ready for README/CV as a tested portfolio/demo with these limits, not an unconditional production-readiness claim. Backend Maven NOT RUN this task because backend unchanged and smoke uses mocks. Focused frontend/workflow whitespace check PASS; pre-existing unrelated README whitespace outside scope remains.

## Frontend completion — Browser smoke checkpoint

- Added minimal @playwright/test1.64.1, playwright.config.ts, e2e/booking.spec.ts, test:e2e script and ignored trace/screenshot artifacts. Browser uses existing local Google Chrome; no browser download was needed.
- Actual browser smoke4/4 PASS (11.2s): guest auth continuation/hold/create/QR/reload without duplicate create/link; mocked persisted-payment success/history/e-ticket/logout; explicit cancellation; unknown-create response loss and history resume; mobile390×844 sign-in/search.
- Local production Next server127.0.0.1:3100, all /api/v1 responses intercepted, requests outside localhost blocked. No backend/provider/database was contacted. This is real browser frontend smoke with mocked API, not cross-stack E2E or real webhook verification.
- Initial sandbox attempt NOT RUN (server listen EPERM). Escalated local run PASS, exit0. Command `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm --prefix frontend run test:e2e`; log /tmp/frontend-completion-e2e.log.
- Browser config defaults to installed Playwright Chromium on other machines; local Chrome path supplied by environment. External deployment cookies/CORS/provider integration still NOT VERIFIED.

## Frontend completion — Quality gates checkpoint

- pnpm12.3.4/pnpm-lock.yaml remains the sole dependency source. Added ESLint10.13 (Next16.4.1 supports10; initial9 was registry-EOL), eslint-config-next16.4.1 flat config and `pnpm lint`. Core correctness/hooks rules error; existing dynamic-form any/effect-restoration compiler recommendations remain visible warnings to avoid a broad refactor. CJS require allowed only in Node test harness.
- Removed unused imports/handlers/calculations, moved render-time callback ref updates to layout effects, removed redundant guarded-form early returns now handled by outer wrappers. Fixed JSX entities/internal link. Found missing manual unknown-booking reconciliation control during unused-import review; connected existing guard/imports to history UI.
- Added .github/workflows/frontend-ci.yml: Node24, pinned pnpm12.3.4, frozen install, tests/typecheck/lint/prod audit/production build. Existing backend CI workflow unchanged (still ./mvnw clean verify). Actual GitHub run not triggered/verified.
- Build script intentionally `next build --webpack` because local Turbopack PostCSS IPC is denied; CI and Vercel now inherit the same explicit bundler choice. No Next/framework rewrite.
- `npm --prefix frontend test`:65/65 PASS, failures/skipped0; typecheck PASS; lint exit0 with0 errors/52 visible warnings; production build PASS, exit0. Logs /tmp/frontend-completion-phase2-{tests,lint,build}.log. No browser E2E yet; Chrome found at /Applications/Google Chrome.app, Playwright not previously installed.

## Frontend completion — Passenger validation checkpoint

- F15 frontend-only changes: age/category at serialized first-flight departure date (matching backend OffsetDateTime.toLocalDate), birthday/leap-day boundaries, normalized duplicate seated documents, optional child/infant documents and infant guardian sharing; removed unsupported passport-expiry/digits-only rules. Existing active-booking duplicates remain server-enforced via PASSENGER_ALREADY_BOOKED_ON_FLIGHT; no speculative API calls or auto retry.
- Directory explicitly rejects clearing an existing document (backend omission preserves it); update refetches canonical server data. No document-delete endpoint or international passport rule invented. Directory selection/quickfill uses departure reference date when booking.
- Files: lib/passenger-validation.ts, PassengerDetails.tsx, PassengerDirectoryModal.tsx, tests/passenger-validation.test.cjs. `npm --prefix frontend test`:65/65 PASS, failed/skipped0; typecheck PASS. No backend changes. Around-midnight backend date vs airport-local calendar remains a documented business-rule limitation.

## Frontend dependency security checkpoint — 2026-10-29

- Local only; no business logic/frontend source/test/backend changes (SHA256 verified against task-start app/components/lib/tests snapshot), commit/push/deploy or production data access. Previous local work preserved.
- Canonical pnpm12.3.4 / frontend/pnpm-lock.yaml; conflicting package-lock.json removed as requested. Existing CI backend-only; actual cloud install history unverified. Added frontend/vercel.json pinned pnpm install/build commands; hosting Root Directory must be frontend. README commands updated only.
- Next16.3.3→16.4.1 (minimum patched16.x for October critical GHSA-2464-fhr4-w9xw and GHSA-673q-mjm6-r5xr; earlier next/og RCE fixed16.3.6). PostCSS8.5.6→8.5.23; shadcn4.19→4.23, now dev/build dependency (CSS import only). Targeted compatible transitive patches for nanoid/source-map-js/brace-expansion/ip-address/browserslist/baseline-browser-mapping. No audit fix/force, major direct upgrades, overrides or ignores.
- Fresh pnpm audit baseline100 unique GHSA records (critical4/high40/moderate46/low10). After:1 low only, exit1;99 baseline GHSA IDs resolved. Production audit0, exit0. Previous npm report11 was a different lockfile graph and aggregation.
- Remaining GHSA-gcqx-w48m-w2h4 / CVE-2026-93753: deepmerge4.3.1 via dev-only shadcn/@shadcn/registry; no published patched release. CLI/build risk with untrusted config/registries remains; moving to dev is not a supply-chain security clearance.
- Frozen install PASS; tests54/54 PASS (14 React DOM/jsdom; failed/cancelled/skipped0); TypeScript PASS; Next16.4.1 production webpack build PASS. Default Turbopack still fails locally with PostCSS IPC bind restriction. No browser E2E. Focused frontend diff check PASS; full check retains pre-existing README whitespace errors.
- Detailed IDs, affected/patched ranges, dependency paths, classification and exact commands: docs/testing/FRONTEND_DEPENDENCY_SECURITY.md. Logs /tmp/frontend-security-{before,after,production}.json and /tmp/frontend-security-{tests,typecheck,build,turbopack}.log.

## Frontend final implementation verification — 2026-10-29

- Current Git HEAD verified: `4a2fe48`; all frontend changes are local/uncommitted. No commit/push/deploy, real payment, backend HTTP/database writes, or backend source/schema/API/business-rule changes. Pre-existing README/api-plan edits and coding-rules/seed SQL deletions preserved.
- Completed groups sequentially: Payment15 tests → Authentication22 → Pricing/Timezone29 → Deployment32 → UX50. Each checkpoint ran TypeScript successfully before the next group. Final review added bounded HTTP waits/storage-failure guards, round-trip payload and history regression coverage: **54 distinct tests PASS, failures0/cancelled0/skipped0**; includes14 React DOM/jsdom component tests (not browser E2E).
- Reinstalled dependencies from declared **pnpm12.3.4 frozen lockfile** (`CI=true npm exec --yes --package=pnpm@12.3.4 -- pnpm install --frozen-lockfile --ignore-scripts --store-dir /tmp/flight-frontend-pnpm-store`):PASS,409 packages. Initial npm native-binary troubleshooting had changed local installed versions without lockfile changes; final checks below were rerun after frozen pnpm restoration. Both existing lockfiles include qrcode/types and dev-only jsdom.
- Final commands from repository root: `TZ=America/Los_Angeles npm --prefix frontend test` →54/54 PASS, exit0; `npm --prefix frontend run typecheck` →PASS, exit0; `NEXT_TELEMETRY_DISABLED=1 BACKEND_API_URL=https://backend.example.invalid/api/v1 npm --prefix frontend run build -- --webpack` →PASS, exit0. Production routes `/`, `/_not-found`, `/payment/[result]`. Placeholder upstream was not contacted. Logs: `/tmp/frontend-final-tests.log`, `/tmp/frontend-final-typecheck.log`, `/tmp/frontend-final-build.log`.
- Default `npm run build` (Turbopack) did NOT pass here: initial missing SWC artifact resolved locally; next failure is PostCSS/Turbopack IPC port bind `Operation not permitted`, including escalated attempt. Webpack build fallback passed without changing the build script/framework. No browser or cloud deployment integration claimed. No ESLint script/config available; lint not run. Backend Maven suite not rerun because backend diff is empty. `git diff --check -- frontend`:PASS.
- F01–F09 frontend fixes implemented, plus F10 same-origin proxy/callback routes; F10 deployment config still must be set/verified on hosting. F11/F13/F14/F16/F17/F18 fixed in the covered scenarios. F12 pagination/unique passengers/both legs and F20 mobile account entry/basic dialog keyboard handling improved. Payment resume navigates to `/payment/status?bookingId=...`, avoiding nested payment components. Unknown booking creation remains fenced across remount/reload and requires explicit manual history reconciliation; no exactly-once/idempotency API added.
- Configuration template: **frontend/environment.example** (trackable). `.env.local.example` is ignored by existing root `.env*` rule. Browser uses `/api/v1`; server requires HTTPS BACKEND_API_URL in production. Existing backend settings required: COOKIE_SECURE=true, CORS_ALLOWED_ORIGINS=<frontend origin>, PAYOS_RETURN_URL=<frontend>/payment/success, PAYOS_CANCEL_URL=<frontend>/payment/cancel. No backend files edited.
- Remaining: F15 birthday-at-departure/document validation edge cases and directory document deletion semantics; F19 some form `any`/large components; F20 full accessibility/responsive/browser checks; F21 README/API/status historical drift and frontend lint/CI gate; F22 unused/assets cleanup. Temporary result-list leg selection is not fully persisted. Client countdown is only an estimate; server enforces deadlines. Real payOS signature/provider behavior, hosting cookie/CORS, mobile screenshots and browser E2E remain unverified.
- **Dependency risk found by actual npm audit (package-lock):11 advisories =1 critical/6 high/3 moderate/1 low**, exit1; report `/tmp/frontend-final-audit-dependencies.json`. Includes Next16.3.3 package-level critical next/og advisory GHSA-vcvr-r3jv-pc5j and other advisories. Project does not currently import next/og, but this is not a general security clearance. Audit suggests Next16.4.3 for the aggregate Next findings; no auto audit-fix/framework upgrade performed. Evaluate dependency patching and both lockfiles before any public deployment.

## Frontend implementation — UX checkpoint (2026-10-29)

- F11/F13/F14/F16/F17/F18 implemented locally: no fake QR/account/barcode/gate/terminal/ticket synthesis; demo e-ticket wording and DEMO badge; search response fencing, all-page loading, resettable dynamic price cap; requested cabin/available-only lowest-price assignment; live hold response expiry; conflict map refresh and no historical-click ownership; safe navigation without implicit cancellation; owner/version/prerequisite-validated drafts and in-progress passenger-form autosave.
- Additional F12/F20 improvements: all booking-history pages, unique passenger count and both legs; account nav visible on mobile; dialog keyboard focus/Escape behavior.
- `npm --prefix frontend test`:50/50 PASS (12 actual React DOM/jsdom interaction tests), failures/errors/skipped0; `npm --prefix frontend run typecheck`:PASS, exit0. Later final-review changes require final regression rerun below. No browser E2E, real payment, backend/database writes, commit/push/deploy.
- Files: booking page/session helper, search/seats helpers, use-dialog hook, Home/FlightResults/SeatSelection/PassengerDetails, payment and history/ticket/auth dialogs; tests/components.test.cjs and tests/ux.test.cjs. jsdom is dev-only, qrcode encodes provider payload locally; both lockfiles updated with pnpm12.3.4 lockfile-only check PASS.
- Build remains blocked by missing SWC binary despite package metadata being installed; investigating local dependency artifact only. No backend source changes. Existing F15 document/age validation edge cases and README/lint/CI/unused cleanup remain deferred.

## Frontend implementation — Deployment checkpoint (2026-10-29)

- F10 local implementation: browser API base fixed to same-origin /api/v1; Next rewrite targets validated server-only BACKEND_API_URL (HTTPS required in production). New /payment/[result] client route validates bookingId and reads authenticated backend status for either success/cancel; query status cannot mark paid.
- Files: next.config.mjs, lib/deployment.mjs, lib/api.ts, app/payment/[result]/page.tsx, .env.local.example, tests/deployment.test.cjs.
- `npm --prefix frontend test`:32/32 PASS (3 deployment), failures/skipped0; `npm --prefix frontend run typecheck`:PASS, exit0. No backend source/contract changes or deployment.
- Required deployment configuration remains external/unverified: BACKEND_API_URL, existing backend COOKIE_SECURE=true, CORS_ALLOWED_ORIGINS=<frontend origin>, PAYOS_RETURN_URL/PAYOS_CANCEL_URL=<frontend>/payment/success|cancel. Same-origin rewrite forwarding cookies has not been browser-tested on hosting.

## Frontend implementation — Pricing/Timezone checkpoint (2026-10-29)

- F07/F08: typed flight adapter retains raw instants, airport timezones, numeric price and actual duration. Flight cards/filters, itinerary and tickets use airport timezone; calendar carousel uses UTC calendar arithmetic. Countdown and datetime no longer share formatter. No invented boarding time.
- Shared pricing mirrors backend CHILD75%, INFANT10% per leg, per-seat HALF_UP cents and10% tax on fares plus add-ons. Estimates clearly labelled; actual payment uses backend total. Return extras explicitly excluded by current outbound-only payload semantics.
- Files: lib/flight.ts, lib/pricing.ts, lib/format.ts; app/page.tsx; FlightResults, PassengerDetails, SeatSelection, MyBookingsModal, BoardingPassModal; SearchParams and tsconfig target ES2020 for exact bigint cents.
- `npm --prefix frontend test`:29/29 PASS (7 new pricing/timezone), failures/skipped0; `npm --prefix frontend run typecheck`:PASS, exit0. No backend business rule or API changes. Browser timezone/print testing not yet run.

## Frontend implementation — Authentication checkpoint (2026-10-29)

- F05/F06/F09: authoritative observable auth snapshot; memory-only access token; shared refresh promise and epoch fencing; logout drains rotation, clears private drafts and blocks silent re-login on failure. Invalid credentials preserved. Seat continuation checks live token rather than stale render boolean. Account switch discards mounted private booking UI and owner-tagged session restoration rejects foreign/legacy drafts.
- Files: lib/api.ts, lib/auth-context.tsx, lib/payment.ts, app/page.tsx, UserNav.tsx, SeatSelection.tsx, tests/auth.test.cjs.
- `npm --prefix frontend test`:22/22 PASS (7 auth +15 payment), failures/skipped0; `npm --prefix frontend run typecheck`:PASS, exit0. One earlier invocation at repository root failed ENOENT; corrected working directory/prefix.
- No backend changes or external auth/payment requests. Browser/cross-tab cookie integration still not run; same-origin proxy is next deployment phase.

## Frontend implementation — Payment checkpoint (2026-10-29)

- Local only; no backend source/schema/API changes, payment provider calls, commit/push/deploy. Existing unrelated changes preserved.
- F01–F04: local QR encoding from payOS payload, validated checkout URL; server status before create-link; pending/terminal/refund recovery; countdown never cancels; explicit booking confirmation and session marker block unknown-outcome create retries. History can resume payment by booking ID. Payment success reuses BoardingPassModal instead of synthesizing tickets.
- Added Node built-in regression runner (TypeScript transpilation; no browser) and 15 payment tests. `cd frontend && npm test`:15/15 PASS, failures/skipped0; `npm run typecheck`:PASS (exit0). Initial typecheck failed until qrcode/types dependency installation completed.
- Remaining: browser integration not run; same-origin deployment and account-bound draft lifecycle are subsequent phases; QR signature/provider integration not tested. Unknown booking outcomes require manual history reconciliation; no idempotency contract added.

## Latest checkpoint — Daily demo routes committed/pushed

- Commit `4a2fe48`: `feat: generate all configured demo routes daily`,7 backend code/config/test files,76 insertions/16 deletions. No Markdown or unrelated seed SQL deletion included.
- `git push origin main` succeeded: `d870b20..4a2fe48`. No manual deployment or Supabase access/write. CI/Render deployment status not checked.
- Precommit full verification129/129 PASS as recorded below. Markdown and pre-existing unrelated changes remain local; index empty.

## Latest checkpoint — Daily demo routes (local implementation)

- HEAD `d870b20`; current changes local only. No Supabase connection/write, deploy, commit or push.
- Default capacity12 flights/day (was10). Templates now select all six configured return pairs daily when capacity permits; larger configured rosters rotate deterministically under the cap. Same slots/date identity, insert-only existing occurrences, 30-day window,2h lead and90000 window-seat budget retained.
- Reuse candidate aircraft when schedule permits; fallback identity `DEMO-<airline>-R<outbound-slot>` separates concurrent daily routes and avoids legacy shared `DEMO-<airline>-01`. No old aircraft/flights/seats updated. No migration, Payment or frontend changes.
- RED: clean single new daily-coverage test1 failure/0 errors, expected360 flights but actual180; `/tmp/daily-demo-red.log`. An earlier non-clean attempt hit stale compiled classes and is not business RED evidence.
- Final targeted command `./mvnw clean '-Dit.test=DemoFlightGeneratorIT,FlightSearchIT' test-compile failsafe:integration-test failsafe:verify`:7/7 PASS, failures/errors/skipped0; `/tmp/daily-demo-targeted-final.log`. Real PostgreSQL/Redis Testcontainers. Coverage includes12 directed routes×30 days, no aircraft overlap, independent concurrent runs/idempotency, rollback, missing airline isolation, history/holds, legacy shared-fallback reconciliation and safety/budgets. Default fixture360 flights/71640 seats; all180-seat fallback deployments64800 seats.
- Full `./mvnw clean verify`:129/129 PASS (Surefire33/Failsafe96), failures/errors/skipped0, exit0. XML reports independently summed; `/tmp/daily-demo-full-verify.log`. TypeScript not run because frontend unchanged. Focused backend diff whitespace check passed; existing unrelated Markdown changes preserved.
- Render: existing generator guards stay required; set `DEMO_FLIGHTS_MAX_FLIGHTS_PER_DAY=12` if currently10. Keep `DEMO_FLIGHTS_MAX_WINDOW_SEATS=90000`. New routes/airport dropdown expansion and the separately observed unfiltered public search500 are deferred.

## Latest checkpoint — Commit/push code, excluding Markdown

- Commit `d870b20`: `feat: add rolling demo flights and harden payment lifecycle`,24 code/config/test files,1447 insertions/88 deletions. Contains Task1/Task2 and demo generator/V9/V10/source contract; no Markdown or existing seed SQL deletion.
- `git push origin main` succeeded: `f74423c..d870b20`. No manual deployment or Supabase connection performed; GitHub Actions/Render actual runs not inspected.
- Fresh precommit `./mvnw clean verify`:127/127 PASS (Surefire33/Failsafe94), failures/errors/skipped0, exit0,2026-10-29 12:57:54 +07; `/tmp/precommit-flight-generator-verify.log`. TypeScript check already passed after latest contract edit.
- Index empty after commit; Markdown and `docs/seed_mock_data.sql` deletion remain local. This status update is local and not committed, per user instruction.

## Latest checkpoint — Remove unused CRAWLER source

- Removed CRAWLER from Java/TypeScript FlightSource. V1/V9 left immutable; new V10 replaces current CHECK with MANUAL/DEMO only.
- V10 deliberately refuses upgrade if legacy CRAWLER rows exist; no automatic relabel/update/delete of historical flights. No Supabase access or production data changes; resolve provenance explicitly if deployment encounters this guard.
- `./mvnw clean -Dit.test=FlightSearchIT test-compile failsafe:integration-test failsafe:verify`:1/1 PASS, failures/errors/skipped0, exit0; `/tmp/remove-crawler-it.log`,2026-10-29 12:30:35 +07. Flyway10 migrations, real PostgreSQL15/Redis7; test verifies CRAWLER write rejected and MANUAL row unchanged. TypeScript `tsc --noEmit --incremental false`:PASS, `/tmp/remove-crawler-tsc.log`. No new full regression run;127/127 above/below is prior generator run.
- Demo templates retained: user asked for explanation, not deletion. GitHub push ships code only; no replication of local rows to Supabase. Runtime generator writes to configured datasource when explicitly enabled. No commit/push/deploy.

## Latest checkpoint — Rolling-window Demo Flight Generator

Local implementation only, HEAD `f74423c`; no Supabase access, deploy, commit or push. Prior Task1/Task2/local edits preserved. New11 files / modified8 files in this task (includes frontend source union only).

- V9 adds DEMO source CHECK + unique demo flightNo index. No master data inserted by migration. Source/layout factory extraction preserves existing admin seat layout and pricing.
- YAML defaults VN/VJ/QH, domestic/international alternating by deterministic roster/date. 30 days in Asia/Ho_Chi_Minh, lead2h, startup + hourly scheduled reconciliation after application readiness. Existing airlines only; missing/unconfigured data is never inferred. Candidate aircraft reused if valid/nonoverlapping; insert-only fallback DEMO-<CODE>-01 at180 seats. Existing master records/occurrences are never updated/deleted.
- Atomic writer uses real DB transaction for Flight + all seats/fallback; conflict skips occurrence. Transaction-scoped PG advisory lock serializes budget admission across instances; DB unique index remains identity authority. Caps10 flights/day and90000 future-window seats. No Redis hold mutations; no Booking/Ticket/Payment changes.
- Guard disabled by default, requires demo/dev and excludes prod/production; exact allowed datasource URL opt-in at orchestration and every writer boundary before writes. Unsafe environment/limits block. Template validation rejection skips whole run without writes; individual runtime failures log and continue, DB connection outage stops that attempt. Normal Flyway/database context startup can still fail independently.
- Public search now applies upcoming predicate in content/count queries, admin retains old overload/history. Frontend only extends FlightSource union with DEMO; no Next.js or Payment changes.
- Phase checks: compile after layout/config, writer/generator, and public search PASS. Targeted `./mvnw clean '-Dit.test=DemoFlightGeneratorIT,FlightSearchIT' test-compile failsafe:integration-test failsafe:verify`:5/5 PASS, failures/errors/skipped0; `/tmp/demo-targeted-complete.log`. First targeted run4/5: budget setting leaked between test methods; fixed fixture reset, no assertions relaxed. TypeScript `./node_modules/.bin/tsc --noEmit --incremental false`:PASS, exit0, `/tmp/demo-typescript.log`.
- Five IT groups use real PG/Redis:180 flights/39240 seats; independent concurrent instances + rerun; future-effective fourth airline via config; real SQL failure rolls back flight/seats/fallback; missing airline isolation; 90-day downtime preserves existing Flight/Booking/Ticket/Payment and HELD/BOOKED inventory; safety/budget/template guards; SIN/ICN timezone and public/admin pagination. No payOS calls.
- First full verify127/127 PASS (Surefire33/Failsafe94), `/tmp/demo-full-verify.log`; final `./mvnw clean verify` after soft template-validation handling: **127/127 PASS**, Surefire33 + Failsafe94, failures/errors/skipped0, exit0 (2026-10-29 11:06:10 +07), `/tmp/demo-full-final.log`. XML independently checked33+94. All5 new IT groups and original122 tests PASS; no cumulative repeated-run counting. Java22.0.2/release21, real PostgreSQL15.19/Redis7, Flyway9 migrations.
- Remaining: no cleanup/durable retry, DB growth persists, no UI badge, aircraft suitability relies on approved configuration/known schedules (no hard global reservation constraint against concurrent admin edits). Supabase pause cannot be repaired by generator. Row-count budgets do not guarantee total database bytes. New config versions must start after last generated date; do not reassign slot IDs. No JDK21/PG17 runtime or real cloud verification.

## Latest checkpoint — Task 2: Webhook Idempotency / Terminal Payment States

Task hiện tại: xử lý riêng HIGH #3/#4 và amount rounding sau Task1. HEAD vẫn `f74423c`, không commit/push/deploy; giữ mọi pre-existing changes, không frontend/schema/dependency change.

- HIGH #3 sau Task1 **đã PASS trước Task2**: Booking → Payment locks + refresh của Task1 bảo vệ HTTP OSIV snapshots. IT mới pause cả hai HTTP requests sau real payment read, assert entities còn managed, rồi cho hai independent transactions cạnh tranh booking row lock (`pg_blocking_pids`, PID khác nhau). Test-only PostgreSQL trigger đếm đúng1 UPDATE/payment; SUCCESS chỉ phát1 PaymentSuccessEvent và persist1 notification. Không tự bỏ Task1 guard để tạo RED giả.
- HIGH #4 vẫn RED: callback late lặp sửa metadata/updated_at, REFUNDED trở về REFUND_REQUIRED. Fix giới hạn trong PaymentServiceImpl: dưới existing locks/refresh, SUCCESS hoặc PAID_AFTER_TERMINAL hoặc refund marker khác NONE → acknowledge/no mutation. CANCELLED/EXPIRED + NONE vẫn được ghi nhận first late receipt → PAID_AFTER_TERMINAL/REFUND_REQUIRED; không issue tickets/reopen booking. Không thêm refund endpoint/provider operation.
- VND contract: payOS API amount là integer (https://payos.vn/docs/api/). HALF_UP là policy project đã dùng ở outbound create-link, không phải rule làm tròn do payOS xác nhận. Dùng chung `payableVnd` (setScale0/HALF_UP/longValueExact) ở create-link và webhook. Giữ decimal Booking/Payment snapshots, không sửa fare formula/dữ liệu cũ. RED .50/.55 trước đây bị truncate khi validate webhook, dù provider nhận đúng integer được gửi.
- `WebhookIdempotencyIT`:15 cases, chỉ gateway mock; real PG15/Redis7 + HTTP/OSIV, committed JDBC snapshots, notification completion trước fixture cleanup. Bao gồm success/late HTTP races, sequential duplicates, refund markers, CANCELLED/EXPIRED first receipts, .00/.49/.50/.55, wrong amount, deferred COMMIT rejection SQL23514/no event + successful retry.
- Baseline confirmed trên source sau Task1:7 cases,3 PASS/4 FAIL,0 errors/skipped, exit1; `/tmp/task2-red-confirmed.log`. Concurrency PASS, terminal2 và rounding2 RED. Lượt baseline đầu có1 assertion PID sai vì đo outside transaction; sửa coordination đo PID dưới real transaction/lock, rồi xác nhận lại. Không coi lỗi coordination là business RED.
- GREEN core7/7; targeted mở rộng `./mvnw clean '-Dit.test=WebhookIdempotencyIT,CancelPaymentLifecycleIT' test-compile failsafe:integration-test failsafe:verify`: **27/27 PASS**, failures/errors/skipped0, exit0 (Task2 15 + Task1 12), `/tmp/task2-targeted-expanded.log`. Full `./mvnw clean verify`: **122/122 PASS**, Surefire33 + Failsafe89, failures/errors/skipped0, exit0; `/tmp/task2-full-verify.log`. XML đối chiếu độc lập khớp33+89; giữ Task1 12 và mọi F01–F04 regressions. Summary `/tmp/task2-verification.json`.
- Limitations: in-process commit/event semantics, chưa durable/exactly-once delivery qua crash hoặc listener failure; khác reference tới cùng terminal order được giữ nguyên record cũ, cần đối soát riêng nếu thực sự có transfer mới. REFUNDED được fixture mô phỏng vì chưa có refund workflow thật. Không gọi provider hoặc verify signature thật; chưa JDK21/PG17 runtime. Không sửa hardcoded QR, frontend recovery hoặc duplicate passenger flows.

## Latest checkpoint — Task 1: Cancel / Payment Lifecycle

Task 1 trước đó: sửa riêng BLOCKER cancel/payment; HEAD vẫn `f74423c`, không commit/push/deploy. Các mốc CI bên dưới là lịch sử.

- Root cause: cancelBooking không khóa/đóng payment; webhook chỉ xét payment PENDING; cancelPayment release ghế qua ticket cũ. Persistence context HTTP có thể giữ snapshot trước lock.
- Fix: cancelBooking dùng Booking → Payment row locks, refresh snapshots dưới lock, CANCELLED booking/payment/tickets trong cùng transaction. Ghi cancellation metadata; hủy link provider qua afterCommit. cancelPayment trên booking CANCELLED chỉ đóng legacy PENDING intent/return terminal result, không revisit tickets/ghế; booking COMPLETED bị từ chối.
- Webhook chỉ complete nếu cả Booking và Payment PENDING; payment nhận sau cancellation → PAID_AFTER_TERMINAL/REFUND_REQUIRED, không mutate tickets/seat. Refresh locked snapshots cũng được dùng cho create-link phases để late attachment không ghi lại trạng thái cũ.
- Release ghế dùng conditional SQL: BOOKED + pending ticket của booking đang hủy + không có active allocation của booking khác. Không mutate managed FlightSeat hoặc Redis. F01–F04 Lua/CAS/deadlines/transaction callbacks giữ nguyên. Không migration/dependency/frontend change.
- Provider create vẫn ngoài DB transaction; booking cancellation chỉ submit provider cancellation sau commit; lỗi submit không biến DB commit thành API failure. Best effort, chưa có durable retry.
- Regression `CancelPaymentLifecycleIT`: gateway mock duy nhất, PostgreSQL/Redis Testcontainers thật, asserts JDBC committed state. Hai core RED, bốn concurrency schedules (cả hai cancellation APIs × cancel/webhook thắng trước), ownership, legacy hold, guarded release, deferred commit rejection, hai post-commit provider submission failure cases. Concurrency dùng independent workers/transactions, actual `pg_blocking_pids`, bounded waits/finally cleanup. Không gọi payOS thật.
- RED confirmed: `./mvnw clean -Dit.test=CancelPaymentLifecycleIT test-compile failsafe:integration-test failsafe:verify` → 2 tests/2 failures/0 errors/skipped; `/tmp/task1-red-confirmed.log` (source trước fix). First RED attempt có lỗi fixture airport do base cleanup xóa seed; sửa fixture tạo airports riêng rồi xác nhận RED; không tính fixture error thành business RED.
- GREEN core2/2, expanded8/8, expanded10/10; final targeted **12/12 PASS**, failures/errors/skipped0, exit0; `/tmp/task1-targeted.log` (2026-10-29 01:18:51 +07). Full `./mvnw clean verify`: **107/107 PASS**, Surefire33 + Failsafe74, failures/errors/skipped0, exit0; `/tmp/task1-full-verify.log`. XML reports kiểm tra lại khớp33+74; F01–F04 regression đều PASS. Local Java22.0.2/release21, PostgreSQL15/Redis7; chưa verify runtime JDK21/PG17.
- Limitations: real provider cancellation/signature/network chưa test; callback/refund idempotency toàn diện, hardcoded QR, duplicate passenger trên hai ghế, frontend recovery nằm ngoài Task 1. Không sửa các findings đó trong task này.

## Scope và nguồn tham chiếu

- Task CI trước đó: **audit và sửa tối thiểu GitHub Actions Maven gate** để chạy cả Surefire/Failsafe qua `./mvnw clean verify`; giữ CI functions hiện có, không đổi pom/business logic/tests. Các milestone dưới đây giữ evidence lịch sử.
- Audit/evidence: [PHASE1_AUDIT.md](testing/PHASE1_AUDIT.md), H cho F01, I cho F02, **J cho F03, K cho fixed hold deadline, L cho F04**. Báo cáo không thay thế việc đọc source mới.
- Task CI không sửa business logic, frontend, payment flow hoặc dependencies. Không skip tests, commit/push hoặc deploy; không gọi payment provider thật.

## Latest checkpoint — CI commit và push

- Theo yêu cầu tiếp theo, tạo commit **`f74423c66457b2b66e810264383102a1ba10359d`**: `ci: run unit and integration tests with Maven verify`, chỉ1 file `.github/workflows/ci.yml`,2 insertions/6 deletions. Không commit `.md` hoặc các changes khác.
- `git push origin main` thành công, remote cập nhật `e783773..f74423c`. Push gồm commit F01–F04 `ac8e030` và commit CI `f74423c`. Local HEAD/origin/main đồng bộ tại lúc kiểm tra; index rỗng.
- Verify local95/95 PASS là run00:32:48 dưới đây; không chạy lại tests vì không thay source/config được verify. Push đáp ứng trigger workflow; **chưa xác nhận kết quả actual GitHub Actions run**, không thực hiện deployment.
- Working tree còn7 Markdown paths và2 changes ngoài phạm vi (Dockerfile/seed SQL), giữ nguyên. Status này chỉ cập nhật local.

## Historical checkpoint — CI Maven verify gate

- `.github/workflows/ci.yml`: đổi bước `mvn clean test` thành `./mvnw clean verify`, đổi tên cho rõ unit/IT/JAR; bỏ bước `mvn package -DskipTests` vì verify đã gồm package/repackage. Giữ nguyên triggers/branch/path filters, workflow_dispatch, ubuntu-latest, checkout/setup-java@v4, Temurin21/Maven cache, working-directory backend, toàn bộ env và services PostgreSQL17/Redis7. Workflow hiện không có deployment/publish step; không thêm hoặc thực thi deploy.
- `backend/pom.xml` giữ nguyên (đã so SHA256 và HEAD): Java21, Surefire *Test, Failsafe *IT với cả integration-test và verify goals; versions thực thi3.5.6. Không skip/ignore failures hoặc continue-on-error. Surefire failure dừng build; Failsafe failures được báo nonzero ở verify, làm fail step/job theo semantics Maven/GitHub Actions mặc định.
- IT dùng real PostgreSQL15/Redis7 Testcontainers qua DynamicPropertySource, không phải workflow service PostgreSQL17. Service containers hiện có được giữ theo yêu cầu; random mapped ports của Testcontainers không dùng fixed5432/6379. Không Docker-in-Docker hoặc disable Ryuk/skip-on-no-Docker.
- Static validation PASS bằng Ruby Psych YAML AST và Python XML: cấu trúc/triggers/services/setup/env không đổi; wrapper tracked executable100755, Maven3.9.16; correct directory/exact command/no skips; không thay production/test source. `actionlint` chưa có trên máy nên không claim đã chạy actionlint.
- Docker local29.2.0 Linux/aarch64 qua Docker Desktop hoạt động. Sandbox từ chối socket nên Maven/Docker được chạy ngoài sandbox theo approval. Local runtime chỉ có Java22.0.2, compile release21; chưa verify Temurin21/Linux amd64 runtime.
- `cd backend && ./mvnw clean verify`: **95/95 PASS**, Surefire33 + Failsafe62, failures/errors/skipped0, exit0, kết thúc **2026-10-29 00:32:48 +07:00**. PostgreSQL15.19/Redis7 containers start thật, Flyway8 migrations, không payOS thật. Executable JAR `backend/target/backend-0.0.1-SNAPSHOT.jar` có BOOT-INF/classes và Spring Boot JarLauncher, chứng minh giữ packaging sau khi bỏ duplicate step.
- Evidence `/tmp/ci-local-clean-verify.log`, `/tmp/ci-local-verification.json`, `/tmp/ci-workflow-validation.json`, `/tmp/ci-local-surefire-reports`, `/tmp/ci-local-failsafe-reports`. Đây là local execution, **không phải GitHub Actions run**.
- Cần xác nhận trên GitHub thật: checkout/setup/cache/wrapper download, service/container startup/image pulls, Temurin21 ubuntu-latest runtime,33 Surefire +62 Failsafe cases và status gate nonzero khi có test failure. Không trigger workflow_dispatch/push/deploy trong task. Workflow/docs vẫn local, index rỗng; giữ các changes trước task.

## Historical checkpoint — Commit trước documentation

- Commit **`ac8e0305964e37dca342b89c6da3504709d28df1`**: `fix(booking): enforce safe seat holds and transaction consistency`. Một commit tổng hợp20 files: production fixes, test infrastructure/regressions, pom và `.gitignore`; mọi `.md` cố tình giữ ngoài commit. Các task dựa lên nhau trên working tree nên không chia nhỏ bằng cách dựng lại các phiên bản trung gian.
- Xóa `TestJDBC.java` và `TestJDBC.class`: standalone manual connection probe có hardcoded connection credentials, không references trong build/source và chưa từng tracked. Không in credential values; không xóa JDBC assertions trong JUnit/Testcontainers. `.gitignore` thêm `*.class`.
- Review staged/index: không Markdown, compiled/generated artifact hoặc giá trị secrets từ ignored `backend/.env`; test credentials là dummy. Giữ ngoài commit `backend/Dockerfile` (whitespace-only) và deletion `docs/seed_mock_data.sql` cùng toàn bộ `.md`. Không refactor/sửa production business logic trong task commit.
- Full verify đầu: **94 PASS/1 FAIL/0 errors/skipped**, kết thúc00:14:52; log `/tmp/phase1-precommit-clean-verify.log`. Failure ở `fixedDeadline_initialAcquireUsesExactlyOneDeadline` so Redis acquisition expiry với JVM host clock; exact DB/Redis/response assertions vẫn PASS.
- Đo Redis TIME bằng container tạm cho thấy server chậm hơn host khoảng0.5–0.7ms; combined millisecond truncation có thể vượt lower-bound tolerance1ms. Test acquire/reacquire đổi before/after sang Redis TIME, cùng clock với acquisition; giữ exact DB/Redis/response equality, TTL900, tất cả27 JDBC call sites. Lower bound không cần trừ1ms nữa; không thay production deadlines/logic.
- Narrow2 tests: **2/2 PASS**, failures/errors/skipped0, exit0,00:19:23; `/tmp/phase1-precommit-clock-tests.log`.
- Final `cd backend && ./mvnw clean verify`: **95/95 PASS**, failures/errors/skipped0, exit0, **2026-10-29 00:21:18 +07:00**. XML khớp Surefire33 + Failsafe62; PostgreSQL15.19/Redis7 Testcontainers, Java22.0.2/release21, Flyway8; không gọi payOS thật. Log `/tmp/phase1-precommit-final-clean-verify.log`, report snapshots `/tmp/phase1-precommit-final-surefire-reports` và `/tmp/phase1-precommit-final-failsafe-reports`.
- Post-commit index rỗng; working tree còn7 `.md` paths và2 ngoài phạm vi (Dockerfile/seed SQL), được giữ nguyên. Tài liệu status này chỉ cập nhật local, không commit theo yêu cầu. Manifest20 paths: `/tmp/phase1-commit-manifest.txt`. Chưa push/deploy; chưa chuyển sang sửa README/documentation.

## Latest milestone — F04

PostgreSQL quyết định booking/seat state. Redis vẫn là lease gate, không được dùng một mình để kết luận booking/seat đã persist. Không cố tạo atomic transaction xuyên hai store; crash/outage dùng **cleanup best effort + original TTL**, với giới hạn liveness bên dưới.

- `BookingService.createBooking`: không DEL trong transaction. Mỗi DB claim vẫn dùng F01/F02 CAS và kiểm tra exact token/deadline; `beforeCommit` kiểm tra lại tất cả snapshots. `afterCommit` mới compare-delete; mỗi lỗi Redis được bắt riêng, log không token/payload, không biến Booking đã commit thành API failure. Top-level API call không có outer transaction; response qua Spring transaction proxy chỉ đến caller sau commit, có independent JDBC evidence. Nếu service join ambient REQUIRED transaction, outer caller chịu trách nhiệm hoàn tất commit trước HTTP response.
- `holdSeats`: server tạo candidate UUID, đăng ký rollback cleanup **trước** gọi Redis (bao gồm trường hợp SET thành công nhưng reply mất). Không còn catch/release trong method body. Chỉ known ROLLED_BACK mới enqueue cleanup; COMMITTED/UNKNOWN không xóa candidate theo suy đoán.
- `SeatHoldTransactionLifecycle`: rollback cleanup chạy trong executor nội bộ1 worker/queue128, fresh `REQUIRES_NEW`; đọc DB `FOR UPDATE NOWAIT`, giữ mọi live DB hold, rồi compare-delete đúng candidate. Không xóa lease đã được concurrent retry commit/adopt, không xóa generation khác. Redis read trước DB giúp bỏ qua candidate không từng thắng SET NX. Task rejection/Redis failure/DB contention → original TTL, không mask business exception.
- **Lý do deferred rollback cleanup:** bản đồng bộ đầu tiên pass94 tests nhưng test pool2 phát hiện callback còn giữ connection cũ, `REQUIRES_NEW` chờ thêm connection. Executor hữu hạn trả callback ngay, không CallerRunsPolicy, tránh cycle giữ connection; test pool2 hiện GREEN với SQLState55P03 từ NOWAIT thật. `@Bean(defaultCandidate=false)` + qualifier giữ Boot `applicationTaskExecutor` của payment/email; queue-full test kiểm tra cả việc không thay executor mặc định.
- Không thay Lua RELEASE/DEADLINE/ACQUIRE, key/value `userId:UUID`, hold CAS/final claim queries, hold/payment deadlines, API/schema hoặc dependencies. Repository chỉ thêm cleanup read/lock query. Cancel và payment cleanup tiếp tục không DEL vì không có lease identity; không tự mở rộng cancel/payment concurrency.

### Verification F04

Tại `backend/`, Java22.0.2/release21, PostgreSQL15.19/Redis7 Testcontainers thật, Flyway8 migrations; không payOS thật. Deferred constraint triggers tạo PostgreSQL **commit failure thật**, Redis ACL tạo GET/DEL failure thật; một fault injection ném exception **sau real SET** để mô phỏng reply loss. Không mock persistence/affected rows/Redis command results. Crash tests tái tạo durable state bằng real stores, **không SIGKILL process**.

```sh
# RED trước production change; GREEN core cùng8 cases lúc chưa thêm coverage mở rộng
./mvnw clean -Dit.test=BookingRedisConsistencyIT test-compile failsafe:integration-test failsafe:verify

# Expanded F04 + F02/F03/fixed-deadline
./mvnw clean '-Dit.test=BookingRedisConsistencyIT,RedisLeaseConcurrencyIT,SeatTransitionConcurrencyIT' test-compile failsafe:integration-test failsafe:verify

# Pool2: phát hiện RED mới rồi GREEN sau deferred cleanup
./mvnw clean '-Dit.test=BookingRedisConsistencyIT#rollbackCleanupDoesNotWaitForConcurrentRetryRowLock' -Dspring.datasource.hikari.maximum-pool-size=2 test-compile failsafe:integration-test failsafe:verify

./mvnw clean verify
```

| Stage | Tests | PASS | Failures | Errors | Skipped | Exit | Kết thúc 2026-10-27 +07:00 / log |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| RED production cũ, xác nhận | 8 | 0 | 8 | 0 | 0 | 1 | 14:31:30; `/tmp/f04-red-confirmed.log` |
| GREEN core, cleanup đồng bộ ban đầu | 8 | 8 | 0 | 0 | 0 | 0 | 14:33:17; `/tmp/f04-green-core.log` |
| Expanded ban đầu, F04 14 + RLC22 + STC5 | 41 | 41 | 0 | 0 | 0 | 0 | 14:37:51; `/tmp/f04-green-expanded.log` |
| Full trước phát hiện pool2 | 94 | 94 | 0 | 0 | 0 | 0 | 14:41:03; `/tmp/f04-clean-verify.log` |
| Pool2, cleanup đồng bộ | 1 | 0 | 0 | **1** | 0 | 1 | 14:43:42; `/tmp/f04-small-pool.log`, Future timeout/chờ connection, không phải business RED |
| Pool2, deferred cleanup | 1 | 1 | 0 | 0 | 0 | 0 | 14:49:48; `/tmp/f04-small-pool-green.log` |
| **Final full clean verify** | **95** | **95** | **0** | **0** | **0** | **0** | **14:52:18**; `/tmp/f04-final-clean-verify.log` |

**Final XML khớp Surefire33 + Failsafe62 =95 distinct cases**, failures/errors/skipped0, exit0. Failsafe62 = F04 16 + RLC22 + SeatHold3 + STC5 + BookingConcurrency3 + BookingInvariants12 + context1. Đã archive `/tmp/f04-final-surefire-reports` và `/tmp/f04-final-failsafe-reports`; không cộng repeated runs.

F04 hiện16 cases:8 RED core +8 follow-up (post-commit new generation2, commit visibility1, beforeCommit Redis failure1, NOWAIT1, crash-before-commit state1, orphan-after-commit/double-booking guard1, full queue/default executor1). Core assertions giữ nguyên; test harness thêm FIFO barrier **ngoài service transaction** để chờ deferred rollback cleanup. Base IT cũng drain queue trước truncate/flush, SH/STC thêm barrier trước Redis assertions; không sleep/nới assertions. RLC pause compare-read thay pre-commit DEL, stale cleanup pause ở actual compare-delete trong cleanup transaction; vẫn có real PG lock evidence và same-user ABA assertions.

### Failure modes và giới hạn

- Đã bảo vệ: multi-seat SQL/claim failure → zero partial Booking/Leg/Passenger/Document/Ticket/AddOn và giữ original live leases; real booking commit failure → không success/không consume; real hold commit failure/reply loss → candidate cleanup sau rollback nếu DB cho phép; live retry-adopted/new generation không bị xóa; cleanup sau commit thất bại → booking success và DB BOOKED; queue full/pool contention không đổi original error/deadline.
- **Crash/outage không có durable retry job.** Redis orphan có thể còn tới deadline gốc (tối đa900s cho lease mới, ít hơn khi reuse). Trước hold commit crash: DB rollback khi session disconnect được xử lý, Redis key có thể chặn user khác tới TTL. Sau booking commit/crash trước callback: DB BOOKED vẫn chặn double booking dù key còn/mất/expired. Same-owner retry có thể reconcile theo fixed deadline hiện có; không reset now+900. Không suy ra toàn hệ thống khả dụng trong900s khi DB session/connection/lock còn treo vì partition.
- Không scan/xóa current keys dựa solely on DB AVAILABLE: key có thể vừa SET trước CAS của transaction khác. Không có durable DB lease-token binding nên không chứng minh recovery tức thì an toàn cho mọi generation; không thêm schema/outbox để hứa guarantee đó. Cancel sau cleanup failure có thể để orphan cũ chặn hold mới tới TTL.
- UNKNOWN commit outcome/DB commit acknowledgment loss vẫn có thể là committed booking nhưng client nhận lỗi; không tự báo success hoặc retry-create. Dùng existing booking lookup để kiểm tra; không idempotency-key API/exactly-once HTTP guarantee.
- Final Redis read không atomic với DB commit; cleanup queue/callback mất khi process chết, outage/queue đầy không tự retry nền nhiều lần. TTL là fallback, không gia hạn. Redis post-commit cleanup hiện synchronous best effort nên response có thể chờ command timeout; không đổi Redis/global pool config.
- Chưa process-kill/network-partition/failover/Cluster/clock-skew tests, runtime JDK21/PostgreSQL17, mọi batch lock ordering/cancel-payment races hoặc HTTP booking authorization. F01/F02 CAS chỉ bảo vệ tested hold/create transitions; cancellation/payment DB writers không được chứng minh bởi F04 tests.

F04 được xử lý cho managed transaction outcomes và bounded Redis-orphan fallback đã nêu, **không tuyên bố PostgreSQL/Redis atomic hoặc crash recovery tức thì**. Phase1 chưa đóng; F05–F10/cancel-payment concurrency và các release-environment gaps vẫn giữ scope riêng.

## Historical milestone — Fixed seat-hold deadline

Business rule đã chốt: mỗi seat lease mới có deadline cố định900s; retry cùng authenticated owner trả lại lease/deadline hiện tại. Payment vẫn `createBooking` time +15 phút. Đã kiểm tra frontend thực tế: `page.tsx` đi passengers → seats → payment; `SeatSelection.handleContinue` gọi hold; `VietQRPayment` tự POST booking khi chưa có booking ID. Không sửa frontend, draft/recovery/hold session hoặc payment logic.

- `booking/infrastructure/redis/SeatHoldLeaseStore.java`: `acquireOrReuse` thay `acquireOrRenew`. Lua `SET NX PX900000` tạo lease và trả exact absolute expiry bằng `PEXPIRETIME` trong cùng command; retry Lua compare full `userId:UUID` rồi đọc deadline, không EXPIRE/SET. `Hold` snapshot gồm identity/deadline/acquired; `Lease` identity và Redis value format không đổi. Compare-delete F03 không đổi.
- `BookingService.holdSeats`: dùng chính expiry từ Redis cho DB CAS/response; không dùng request-start time +900. Nếu Redis thiếu nhưng DB cùng owner còn live, key mới dùng `SET NX PXAT` với deadline DB cũ, không restart15m. DB snapshot chỉ là candidate; CAS xác minh row thật.
- `FlightSeatRepository.acquireHoldIfAvailable`: giữ guard AVAILABLE/HELD và cấm BOOKED; thêm candidate expiry > DB `clock_timestamp()`, nhánh live same-owner chỉ chấp nhận `heldUntil = expiresAt`. Không mutate/save stale entity. Query final claim F01 và toàn bộ `createBooking`/cancel/payment code giống snapshot đầu task; transaction boundaries không đổi.
- Sau mọi DB CAS, service compare lại exact identity **và** expiry. Cần thiết khi lease đổi token nhưng cùng owner/deadline: real CAS vẫn trả1, final check từ chối/rollback old request. Không đọc lại rồi adopt generation mới.
- Cleanup chỉ release exact lease **mới tạo bởi request**; batch retry lỗi không xóa hold đã có từ trước. Không thêm compensation/afterCommit/reconciliation F04.
- Response DTO giữ nguyên fields: `expiresAt` là deadline sớm nhất trong batch; `ttlSeconds` là thời gian còn lại làm tròn lên, giới hạn0..900, không luôn900. Mỗi seat vẫn có deadline riêng, không có hold session/batch deadline mới. Controller success text bỏ khẳng định luôn được thêm15 phút.

### Verification mới

Tại `backend/`, Java22.0.2/release21, PostgreSQL15.19 và Redis7 Testcontainers thật, Flyway8 migrations; không gọi payOS. Chạy ngoài sandbox vì Docker socket bị từ chối bên trong. Production giữ nguyên cho tới khi8 tests RED hoàn tất; cùng8 test bodies/assertions được giữ khi GREEN.

```sh
# RED trước sửa; GREEN cùng8 tests sau sửa
./mvnw clean '-Dit.test=RedisLeaseConcurrencyIT#fixedDeadline*' test-compile failsafe:integration-test failsafe:verify

# GREEN mở rộng + F02/F03 regression
./mvnw clean '-Dit.test=RedisLeaseConcurrencyIT,SeatTransitionConcurrencyIT' test-compile failsafe:integration-test failsafe:verify

# Full backend
./mvnw clean verify
```

| Stage | Tests | PASS | Failures | Errors | Skipped | Exit | Kết thúc 2026-10-27 +07:00 / log |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| RED, production F03 cũ | 8 | 0 | 8 | 0 | 0 | 1 | 13:55:38; `/tmp/fixed-hold-red.log` |
| GREEN cùng8 cases | 8 | 8 | 0 | 0 | 0 | 0 | 13:57:41; `/tmp/fixed-hold-green-core.log` |
| GREEN mở rộng + F02/F03 | 27 | 27 | 0 | 0 | 0 | 0 | 13:59:29; `/tmp/fixed-hold-green-regression.log` |
| Full clean verify | **79** | **79** | 0 | 0 | 0 | 0 | **14:01:35**; `/tmp/fixed-hold-clean-verify.log` |

8 RED cases kiểm tra exact DB/Redis/response deadline, retry TTL60s không thành900s, retry batch failure, mixed batch min deadline, missing Redis/live DB, actual DB lock wait qua expiry, concurrent same-lease retries và old retry trước CAS/new same-user generation. Ba case bổ sung sau GREEN: retry sau expiry/payment deadline độc lập, inconsistent deadlines fail closed, và token ABA với cùng deadline. Tổng11 cases mới; RLC hiện22 cases, STC5. Full XML sau clean khớp Surefire33 + Failsafe46 = **79 distinct cases**, failures/errors/skipped0; Failsafe46 = RLC22 + SeatHold3 + BookingConcurrency3 + STC5 + BookingInvariants12 + context1. Có45 booking/hold IT và1 context; không cộng các lượt chạy lặp. Test-only interceptors delegate SQL/Redis thật; independent PID/transactions, `pg_blocking_pids`, bounded latches/polling và finally cleanup. Không mock kết quả hoặc nới assertions F01/F02/F03; primitive renew assertions đổi sang read/check do production đã bỏ renew, STC live retry siết deadline equality.

### Giới hạn và rollout

- **Retry sau expiry không có bảo đảm idempotency xuyên các lượt hold.** HTTP request đến khi lease/DB hold cũ đã hết hạn có thể acquire UUID mới/deadline15m mới nếu ghế còn trống; có thể bị owner khác/BOOKED từ chối. Không phân biệt retry cũ với ý định hold mới bằng payload giống nhau; không thêm idempotency-key API. Request đã snapshot old UUID thì bị từ chối nếu key mất/đổi, không tự reacquire trong cùng call. Hai first acquires đồng thời cùng thấy missing: loser SET NX có thể nhận SEAT_HELD; retry tiếp theo kiểm lease hiện tại.
- Redis key mất khi DB vẫn live cùng owner: không cấp lại15m, chỉ tái dùng deadline DB còn lại với UUID mới. Live Redis/DB deadline không khớp thì fail closed SEAT_HELD, không tự sửa/gia hạn; không phải giải pháp recovery tổng quát.
- **F04 vẫn OPEN:** PostgreSQL/Redis không atomic; crash/network/commit failure, multi-seat consume rollback, orphan reconciliation và compensation chưa xử lý. Final Redis check là guard trước return, không khóa Redis xuyên DB commit. Cùng UUID vẫn là cùng lease, không có fencing mỗi HTTP intent; overlapping transaction/cleanup toàn diện vẫn thuộc F04.
- Yêu cầu **Redis >=7** cho PEXPIRETIME (compose/test hiện dùng7); ACL phải cho phép Lua và commands tương ứng. Không đổi Redis value format/API/schema/dependencies. Drain old writers trước rollout vì binary cũ vẫn renew900/DEL; live holds từ code cũ có thể lệch deadline và bị fail closed tới expiry. Không tự upgrade/delete legacy/malformed/immortal keys; không deploy/flush dữ liệu thật.
- Chưa verify runtime JDK21/PostgreSQL17, Redis Cluster/failover/network partitions/clock skew, mọi lock ordering cho batch khác thứ tự, cancellation/payment concurrency hoặc frontend recovery. Hệ thống vẫn cần đồng bộ wall clocks giữa DB/Redis/app.

## Verified milestone — F01

**F01 đã sửa và verify cho expiry DB tại final claim.**

- `backend/src/main/java/com/thanh/backend/flight/repository/FlightSeatRepository.java:51` giữ predicate F01 `s.heldUntil > cast(function('clock_timestamp') as OffsetDateTime)` trong final claim. Status HELD, authenticated owner ID và affected-row handling giữ nguyên.
- SQL thực tế dùng PostgreSQL `clock_timestamp()` với timestamp with time zone, tránh `CURRENT_TIMESTAMP`/`now()` cố định ở transaction start. Không dùng Java `now` lấy đầu createBooking cho predicate mới.
- `BookingService.createBooking` vẫn là một transaction; claim count0 ném `AppException(SEAT_NOT_AVAILABLE)` và rollback. Task không sửa service hoặc propagation.
- Test `BookingConcurrencyIT#createBooking_HoldExpiresAfterValidation_RejectsFinalClaimAndRollsBack` tại `backend/src/test/java/com/thanh/backend/booking/service/BookingConcurrencyIT.java:448` dừng trước claim bằng test-only interceptor gọi tiếp repository thật. Không mock claim result, không thay save/persist semantics, không có Bob.
- Test xác nhận Alice đã validation/insert khi hold còn hạn, sau đó rút ngắn lease thật và chờ DB deadline/Redis TTL hết hạn tự nhiên. Guard trong transaction chứng minh `CURRENT_TIMESTAMP < held_until <= clock_timestamp()`.
- GREEN assertions: SEAT_NOT_AVAILABLE, response null; committed count0 cho Booking/BookingLeg/Passenger/Document/Ticket/AddOn; seat vẫn HELD expired đúng Alice, Redis key absent. Worker/latch/advisor cleanup có bounded waits/finally.

## Verified milestone — F02

**F02 đã được tái hiện thật sau F01, sửa bằng DB CAS và verify GREEN.**

- Lịch audit cũ dùng hold Alice đã expired không còn khả thi: F01 expiry predicate từ chối old lease. Không mock thời gian/query result để ép Alice thành công.
- Lịch khả thi khác: Bob transaction read AVAILABLE hoặc expired old hold rồi pause; Alice lấy **fresh live hold**, booking hợp lệ và commit; Bob resume với stale managed entity. Trước F02 fix, Redis key đã absent cho Bob SETNX, Hibernate save UPDATE theo id đưa BOOKED về HELD Bob, rồi Bob booking thật tạo **2 active bookings/seat**. Cả2 variants đã RED.
- `backend/src/main/java/com/thanh/backend/flight/repository/FlightSeatRepository.java:22` thêm `acquireHoldIfAvailable`: chỉ AVAILABLE hoặc HELD expired/cùng owner được hold, tuyệt đối không BOOKED; update owner/deadline/updatedAt trong conditional UPDATE.
- `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java:150` gọi hold CAS, count0 → SEAT_HELD. Không mutate/save loaded FlightSeat nên không còn unconditional dirty-check write sau CAS. Transaction boundaries và Redis flow giữ nguyên.
- `backend/src/test/java/com/thanh/backend/booking/service/SeatTransitionConcurrencyIT.java:95` có hai stale-writer variants, hold-vs-hold, booking-vs-booking có actual PostgreSQL lock evidence, và compatibility case renew live hold cùng user: **5 cases**. Không mocks/spies; interceptors chỉ pause/delegate các real calls, independent transactions/PG backend PID, READ COMMITTED được đo thật.
- GREEN: Bob hold bị SEAT_HELD, Bob booking bị SEAT_NOT_AVAILABLE; snapshot sau stale holder vẫn BOOKED/null hold, Redis absent; chỉ1 active booking và1 committed Booking/Leg/Passenger/Document/Ticket/AddOn graph; không Bob partial records.
- Hai booking cùng live hold: actual pg_stat_activity `wait_event_type=Lock`, `wait_event=transactionid`, `pg_blocking_pids` trỏ winner; loser UPDATE recheck sau winner commit trả0, rollback. Hold-vs-hold live Alice lease được Redis chặn từ trước fix, không cố tạo RED cho case đã an toàn.

## Verified milestone — F03

**F03 đã tái hiện5 real-service races trước sửa, rồi GREEN với server-generated unique lease identity và atomic Lua mutations.**

- Redis key giữ `hold:seat:{seatId}`, value mới **`userId:UUID`**. UUID mới mỗi acquire sau missing/expiry, kể cả cùng user; renew giữ token. `SeatHoldLeaseStore.java` bao toàn bộ seat-hold Redis access, Lua compare full identity rồi DEL/PEXPIRE; return false nếu missing/mismatch/wrong actor, không tạo lại key.
- Authentication vẫn từ principal → service User; không nhận userId/token mới trong API. Token chỉ Redis và immutable request-scoped Lease snapshots, không trả response hoặc persist DB. API/schema/hold TTL900/payment deadline15m không đổi.
- Hold cleanup dùng exact leases acquired/renewed trong request, kể cả trường hợp DB CAS thất bại. Booking validation giữ Map<seatId,Lease>, truyền tới consume; consume mismatch → SEAT_NOT_AVAILABLE và DB rollback, không reread/delete lease2.
- Cancel booking không DEL: original hold đã consume lúc createBooking, cancel không có identity của key mới. Test workflow thực tế xác nhận Bob lấy Redis lease2 từ stale initial read, Alice cancel frees DB, Bob hold completes và lease2 còn nguyên.
- `RedisLeaseConcurrencyIT` có11 cases: stale cleanup2, stale renew2 (có same-user ABA), missing-key renew, identity lifecycle, wrong actor/token, missing/expired primitive operations, legacy/malformed, consume snapshot mismatch rollback, cancel giữ newer lease. Không mock Redis command results; test proxies chỉ điều khiển timing, PostgreSQL/Redis thật.
- RED: cleanup xóa lease2; old renew kéo TTL mới từ30s lên gần900s; same-user old renew ghi lùi DB deadline; missing-key EXPIRE=false vẫn báo success. GREEN: raw lease2/TTL nguyên, old mutation rejected, DB/graph được assert fresh JDBC.
- Existing SH/BC/STC assertions cập nhật owner+canonical UUID; Bob/batch preservation so exact raw identity trước/sau. Không nới assertions status/error/rollback; test logs không in UUID.
- F01/F02 FlightSeatRepository byte-for-byte không đổi từ đầu task. Không thêm migration, dependencies hoặc đổi transaction boundaries.

## Historical verification — Redis adapter package organization

- Canonical path: `backend/src/main/java/com/thanh/backend/booking/infrastructure/redis/SeatHoldLeaseStore.java`.
- BookingService giữ business orchestration; SeatHoldLeaseStore giữ Redis key/value, UUID serialization và Lua scripts. Vẫn inject concrete adapter, chưa thêm interface/port hoặc layer khác.
- Chỉ chuyển package/file và thêm import vào BookingService/RedisLeaseConcurrencyIT. Đã so file với snapshot đầu task: toàn bộ body sau package declaration giống hệt, không thay Lua/token/TTL/transaction/API/schema.
- Spring component scan từ BackendApplication vẫn discover adapter; context smoke test xác nhận wiring thành công.

```sh
./mvnw clean '-Dit.test=RedisLeaseConcurrencyIT,BackendApplicationIT' test-compile failsafe:integration-test failsafe:verify
```

Kết thúc **2026-10-27 08:25:33 +07:00**, **12/12 PASS**, failures0/errors0/skipped0, exit0: RedisLeaseConcurrencyIT11 + BackendApplicationIT1. PostgreSQL/Redis Testcontainers thật, không gọi payOS. Log `/tmp/lease-layering-regression.log`.

Không chạy lại toàn bộ68 cases cho package-only move; kết quả full clean verify68/68 bên dưới là mốc F03 trước relocation. Không thêm test mới hoặc nới assertions.

## Historical execution evidence — F03

```sh
# RED trước production change; GREEN core và expanded sau fix
./mvnw clean -Dit.test=RedisLeaseConcurrencyIT test-compile failsafe:integration-test failsafe:verify

# Toàn backend, gồm F01/F02 và mọi nhóm được yêu cầu
./mvnw clean verify
```

Chạy tại `backend/`, ngoài sandbox, PostgreSQL15.19 và Redis7 (`redis:7-alpine`) Testcontainers thật, Flyway8 migrations; Java22.0.2 compile release21. Không gọi payOS.

| Stage | Test result | Exit | Evidence (2026-10-27 +07:00) |
| --- | --- | ---: | --- |
| RED production cũ | 5 tests, **5 FAIL**, errors0/skipped0 | 1 | `/tmp/f03-red.log`, 00:53:22 |
| GREEN same5 races | 5/5 PASS, failures/errors/skipped0 | 0 | `/tmp/f03-green-core.log`, 00:56:42 |
| GREEN expanded | 11/11 PASS, failures/errors/skipped0 | 0 | `/tmp/f03-green-all.log`, 00:59:49 |
| Full clean verify | **68/68 PASS**, failures/errors/skipped0 | 0 | `/tmp/f03-clean-verify.log`, 01:02:50; XML khớp Surefire33 + Failsafe35 |

Failsafe35 = F03 11 + SeatHold3 + BookingConcurrency3 + SeatTransition5 + BookingInvariants12 + context1. Surefire33 giữ Actuator24 + payment8 + scheduler1. Không cộng các run lặp thành distinct coverage. Expected `booking_pnr_key` rejection ở BI không phải failure.

RED cleanup dùng hai independent PG connections: Alice batch giữ uncommitted seat update rồi cleanup pause; sau TTL1 hết hạn Bob/Alice SET NX2 thật và chờ DB lock; late cleanup cũ xóa key2. GREEN Lua không xóa generation mới, old DB transaction rollback rồi new holder commit. Consume/cancel scenarios cũng dùng production service, không ép invalid transaction thành booking thành công; chi tiết timeline ở audit J.

## Lease contract và rollout limitations

- Server snapshots fence generation đã đọc, không fence intent của HTTP request chưa được server đọc lease; không có client-token API mới. Concurrent operations trên cùng UUID được coi là cùng lease, renew không rotate token.
- Legacy userId-only/malformed keys fail closed, không tự upgrade/delete/renew; tự expire theo TTL. Rollout cần drain old writers, không chạy lẫn binary cũ có plain DEL/EXPIRE; có thể đợi hold window900s sau khi old writers dừng. Không deploy/flush dữ liệu thật trong task.
- Không lưu token vào DB vì booking consume snapshot ngay trong request; cancellation không thể xóa current key chỉ vì cùng user. Orphan recovery là F04, không tự thêm DB/API contract.

## Historical execution evidence — F02

```sh
# Trước production fix (RED), sau CAS fix (GREEN)
./mvnw clean -Dit.test=SeatTransitionConcurrencyIT test-compile failsafe:integration-test failsafe:verify

# Full regression sau GREEN và thêm live-renewal compatibility case
./mvnw clean verify
```

Chạy tại `backend/`, ngoài sandbox, PostgreSQL15.19/Redis7 Testcontainers thật, 8 migrations; Java22.0.2 compile release21. Không gọi payOS. Runtime JDK21/PostgreSQL17 chưa được verify.

| Stage | Tests / Result | Exit | Evidence (+07:00, 2026-10-26) |
| --- | --- | ---: | --- |
| RED sau F01, trước F02 fix | 4 tests: 2 PASS, **2 FAIL**, errors0/skipped0 | 1 | `/tmp/f02-red.log`, 23:30:52; cả2 stale writers gây activeBookings2 |
| GREEN sau hold CAS | 4/4 PASS, failures/errors/skipped0 | 0 | `/tmp/f02-green.log`, 23:32:59; cùng assertions, activeBookings1 |
| Full clean verify | **57/57 PASS**, failures/errors/skipped0 | 0 | `/tmp/f02-clean-verify.log`, 23:34:51; XML khớp Surefire33 + Failsafe24 |

Failsafe24 = SeatHoldIT3 + BookingConcurrencyIT3 (bao gồm F01) + BookingInvariantsIT12 + SeatTransitionConcurrencyIT5 + BackendApplicationIT1. Surefire33 = Actuator24 + payment service8 + scheduler1. Test thứ5 STC bảo đảm same-user live renewal vẫn hợp lệ; được thêm như compatibility regression, không nới assertions4 cases trước đó. Log lỗi duplicate `booking_pnr_key` là expected database constraint test.

Không đổi schemas/constraints, entity versioning, dependencies hoặc test configuration. Row-lock do conditional UPDATE là đủ cho bug đã tái hiện; không chọn lock từ initial read qua Redis I/O. Unique active-ticket constraint là defense-in-depth cần đánh giá lifecycle/dữ liệu cũ riêng; không tự sửa schema hoặc Redis lease tokens/Lua.

## Historical execution evidence — F01

Chạy tại `backend/`, PostgreSQL15.19 và Redis7 Testcontainers thật; Flyway8 migrations; Java22.0.2 compile release21. Chưa verify runtime JDK21/PostgreSQL17 trong task này.

```sh
# RED trước production fix; cùng command được chạy lại để xác nhận GREEN sau fix
./mvnw clean '-Dit.test=BookingConcurrencyIT#createBooking_HoldExpiresAfterValidation_RejectsFinalClaimAndRollsBack' test-compile failsafe:integration-test failsafe:verify

# REGRESSION sau GREEN
./mvnw clean '-Dit.test=SeatHoldIT,BookingConcurrencyIT,BookingInvariantsIT' test-compile failsafe:integration-test failsafe:verify
```

| Stage | Result | Exit | Evidence |
| --- | --- | ---: | --- |
| RED trước fix | 1 test, 1 failure, 0 errors/skipped | 1 | `/tmp/f01-red-final.log`, 21:29:59: Alice booking thành công sai, seat BOOKED, sáu bảng mỗi bảng1 row |
| GREEN sau fix | 1 test PASS, 0 failures/errors/skipped | 0 | `/tmp/f01-green-final.log`, 21:32:42: đúng error và rollback |
| REGRESSION | **18/18 PASS**, 0 failures/errors/skipped | 0 | `/tmp/f01-regression.log`, 21:34:18: SeatHold3 + BookingConcurrency3 + BookingInvariants12, XML Failsafe khớp |

Có một lượt discovery lỗi do target class artifact, và hai lượt predicate thử lỗi syntax/query rewriting trước GREEN cuối; chi tiết đủ counts/logs ở audit mục H. Không ghi nhận những lượt đó là PASS hoặc bỏ qua lỗi query để nhận GREEN. `/tmp` và `backend/target/failsafe-reports` là artifacts có thể bị xóa, nên audit giữ trích evidence cần thiết.

Trong task F01, chưa chạy lại full suite/context; baseline audit trước sửa có clean verify51/51. Task F02 hiện đã clean verify57/57, có regression F01. Không dùng số liệu baseline thay kết quả mới.

## Trạng thái Phase 1 và rủi ro còn mở

**Phase1 chưa hoàn thành.** Mốc F03 có34 Phase1 IT trong full run68 (thêm1 context smoke trong35 IT); fixed-deadline task bổ sung11 IT, xem verification mới phía trên; số PASS không chứng minh mọi invariant/schedule/failure mode. F01/F02/F03 resolved trong phạm vi mô tả; các mục sau còn mở.

- **F04 — managed transaction fixes VERIFIED, crash/outage TTL fallback:** xem latest milestone/L. Không còn pre-commit consume hoặc cleanup trong body; residual availability/unknown outcome/recovery limits được ghi rõ. Không dùng PASS count để suy ra atomic cross-store commit.
- **F05 — High, OPEN:** IT/base fixture/Failsafe và một số production changes vẫn chưa commit; CI đang clean test rồi package skipTests, chưa là integration gate.
- **F06–F10 — OPEN:** test race cũ thay save bằng spy, coverage/cleanup gaps, duplicate passenger active-booking concurrency, PNR insert-collision policy và docs drift. Xem audit để giữ đúng severity/evidence.
- F03 consume guard chặn stale/missing lease sau validation với DB rollback ở case một seat; không biến cả PostgreSQL–Redis workflow thành atomic transaction. Redis Cluster/failover, network partitions, client-side fencing và cancel DB concurrency chưa được chứng minh trong task này.

## Git và phạm vi thay đổi

Task F04 sửa `BookingService`/`SeatHoldLeaseStore`, thêm cleanup read tại `FlightSeatRepository`; thêm `SeatHoldTransactionLifecycle` và `booking/config/SeatHoldCleanupConfiguration`. Tests: mới `BookingRedisConsistencyIT`16 cases; base IT/SeatHoldIT/SeatTransitionConcurrencyIT/RedisLeaseConcurrencyIT thêm barrier hoặc đổi pause location, giữ state/error assertions. Status/audit lưu RED/GREEN/pool2 evidence. Không sửa production payment/frontend, schema/API/dependencies/config toàn cục; các local changes trước task được giữ, chưa commit/push.

Task F03 thêm SeatHoldLeaseStore, sửa BookingService Redis lifecycle/maps/consume/cancel DEL; thêm RedisLeaseConcurrencyIT + SeatHoldLeaseAssertions; cập nhật value assertions và redact logging trong SeatHoldIT/BookingConcurrencyIT/SeatTransitionConcurrencyIT; cập nhật audit/status. F01/F02 repository không đổi, không sửa entity/schema/DTO/pom/CI/base Testcontainers/BookingInvariantsIT. Các local changes có sẵn vẫn giữ; chưa stage/commit/push.

Follow-up phân lớp chỉ move SeatHoldLeaseStore sang infrastructure/redis và cập nhật2 imports, cùng audit/status. Không thay logic nghiệp vụ hoặc implementation của adapter; giữ nguyên các local changes khác.

## Handoff

F04 hoàn tất trong phạm vi latest milestone/L: core RED8 → GREEN8, pool2 RED(error) → GREEN1, **final clean verify95/95 PASS**; fixed-deadline và F01–F03 có evidence lịch sử bên dưới. **Không tự động xử lý F05–F10, cancel/payment concurrency hoặc Phase2.** Nếu tiếp tục, đọc AGENTS.md, status/audit rồi inspect lại git/source/test trước khi dựa vào các kết luận này.
