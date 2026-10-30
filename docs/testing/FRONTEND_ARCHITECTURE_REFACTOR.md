# Targeted Frontend Architecture Refactor — 2026-10-30

## Phạm vi

Thực hiện tuần tự ba refactor đã được duyệt, trên source local tại HEAD `4a2fe48`. Giữ Next.js App Router, thư mục cấp cao, UI/UX và backend contracts. Không thêm dependency hoặc Redux/Zustand, không sửa backend/CI, không commit/push/deploy hay tác động database/payment provider.

Working tree có nhiều thay đổi từ các task trước. Số file bên dưới được tính so với snapshot lúc bắt đầu task tại `/tmp/flight-architecture-baseline`, không tính tất cả thay đổi so với Git HEAD.

## Phase 1 — Checkout contracts

- `lib/checkout-model.ts` sở hữu SearchParams, contact/passenger/draft/checkout/session types. `lib/booking-session.ts` không còn import type từ HomePage. Các props/callbacks passenger/seat/payment dùng model/DTO rõ ràng; không dùng `as any` để vượt compiler.
- Guardian input chuẩn hóa tại boundary HTML/legacy draft thành số nguyên an toàn không âm hoặc null. Validator không nhận string từ dropdown. Test tương tác DOM xác minh index0 và1 đều tiếp tục được. Request builder yêu cầu guardian adult hợp lệ cho infant; optional `guardianPassengerIndex` của non-infant vẫn undefined/không serialize. Không đổi backend để chấp nhận nullable form state.
- `lib/checkout-requests.ts`: pure passenger/booking builders, không gọi API hoặc ghi storage. Giữ tripType, flightIds, original passengerIndex, infant không có ghế, outbound-only add-ons và quantity hiện có. Payload cuối vẫn dùng types/api.ts.
- Xác nhận không có consumer của `types/index.ts` rồi xóa duplicate DTOs. Không sửa `types/api.ts` hoặc phát minh DTO/backend rule mới.
- Checkpoint: **74/74 tests PASS**, failed/cancelled/skipped0, **TypeScript PASS**. Thêm5 pure tests +2 component tests so với baseline67.

## Phase 2 — PassengerDetails

- `components/passenger/use-passenger-form.ts` sở hữu form/draft state, auth autofill, directory reads/saves, quickfill, add-ons, pricing, autosave và continue orchestration. Directory responses được bỏ qua sau effect teardown/unmount. Autosave vẫn dùng callback và schema hiện có.
- `ContactFields`, `PassengerFields`, `PassengerAddOns` là controlled presentation components; không gọi API, ghi storage hay quản token. PassengerDetails giữ screen composition, navigation và directory-modal visibility/selection. Không di chuyển toàn project sang features/src.
- Contact/passenger validation nằm trong pure `validatePassengerDraft`; DTO mapping dùng request builders từ Phase1. Test có thể xác minh lỗi contact/passenger và payload độc lập React.
- Giữ markup/styles, ids, placeholders, quickfill theo ngày khởi hành, contact copy, checkbox save directory, giá ước tính và chuyển bước. PassengerDetails từ1.100 dòng baseline còn295 dòng composition; giảm trách nhiệm là mục tiêu, không dùng số dòng làm tiêu chí duy nhất.
- Checkpoint: **76/76 tests PASS**, failed/cancelled/skipped0, **TypeScript PASS**. Thêm1 pure validation test và1 component test kiểm tra quickfill → copy/edit contact → meal/baggage → autosave → remount → continue, bao gồm document/gender và tax/add-ons.

## Phase 3 — API/Auth separation

Dependency direction:

```text
api facade ──> auth-session ──> http
           └─> domain-api  ──> injected authenticated request
           └─> private-session cleanup injected into auth-session

checkout views/controller ──> checkout model / validation / request builders
```

- `lib/http.ts`: base URL validation, bounded fetch, JSON/envelope handling, headers/credentials and ApiError. Không biết auth store, refresh policy hoặc checkout storage keys. Lifecycle guard được inject và kiểm tra trước/sau response decoding.
- `lib/auth-session.ts`: một lifecycle riêng cho mỗi client, memory-only token/user, epoch fencing, single-flight refresh, one401 retry policy, login/logout và cross-tab broadcast. Logout vẫn invalidate trước, drain refresh rồi revoke cookie. Không đổi cookie/JWT contract.
- `lib/domain-api.ts`: typed methods hiện có cho passenger/booking endpoints. Dùng request function được inject, không tự tạo auth store.
- `lib/private-session.ts`: tên checkout/booking-attempt keys và cleanup legacy caches. Schema/key values giữ nguyên; auth coordinator nhận cleanup callback thay vì biết từng booking key.
- `lib/api.ts` giữ facade singleton cho consumers hiện tại, đồng thời export createApiClient để tests dùng lifecycle riêng. Không yêu cầu sửa hàng loạt consumers. Generic get/post vẫn tồn tại để tương thích.
- Thêm epoch check sau refresh trước khi replay401: nếu logout/account change xảy ra trong lúc chờ thì request cũ bị SESSION_CHANGED, không replay mutation sang session khác. Không thêm retry network/5xx hoặc retry unknown createBooking outcome.
- Checkpoint cuối: **80/80 tests PASS**, failed/cancelled/skipped0, **TypeScript PASS**. Thêm4 auth tests: client isolation; concurrent401 dùng một refresh; logout trong khi mutation đợi refresh; account switch trong lúc decode response. Các test refresh/logout/account-switch/unknown-create cũ giữ nguyên assertions; auth setup dùng factory thay module-cache deletion.

## Files thay đổi trong task

**24 frontend files:12 modified,11 added,1 removed.** Cộng2 tài liệu: PROJECT_STATUS.md được cập nhật, báo cáo này mới; tổng26 paths. Package manifest/lockfile không thay đổi so với task start.

| Loại | Files |
| --- | --- |
| Modified UI/composition | app/page.tsx; components/home-page.tsx; components/flights/FlightResults.tsx; components/passenger/PassengerDetails.tsx; components/seats/SeatSelection.tsx; components/payment/VietQRPayment.tsx |
| Modified helpers | lib/api.ts; lib/booking-session.ts; lib/passenger-validation.ts; lib/payment.ts |
| Modified tests | tests/auth.test.cjs; tests/components.test.cjs |
| Added controlled views/controller | components/passenger/ContactFields.tsx; PassengerFields.tsx; PassengerAddOns.tsx; use-passenger-form.ts |
| Added model/builders | lib/checkout-model.ts; lib/checkout-requests.ts |
| Added API/auth/storage boundaries | lib/http.ts; lib/auth-session.ts; lib/domain-api.ts; lib/private-session.ts |
| Added tests | tests/checkout.test.cjs |
| Removed | types/index.ts — no consumers found; types/api.ts retained unchanged |

## Verification

Lệnh từ repository root trừ nơi ghi khác. Dependencies đã cài bằng pnpm từ task trước; npm ở đây chỉ là script runner. Không cài package hoặc tạo package-lock.

| Check | Kết quả |
| --- | --- |
| `npm --prefix frontend test` sau mỗi phase |74/74 →76/76 →80/80 PASS, mỗi lần failed/cancelled/skipped0 |
| `npm --prefix frontend run typecheck` sau mỗi phase | PASS, exit0 |
| `npm --prefix frontend run lint` cuối | PASS, exit0; **0 errors/38 warnings**, không thay lint config/rules |
| `NEXT_TELEMETRY_DISABLED=1 BACKEND_API_URL=https://backend.example.invalid/api/v1 npm --prefix frontend run build` | PASS, exit0; Next16.4.1/Webpack, placeholder upstream không được gọi |
| Từ frontend: `./node_modules/.bin/tsc --noEmit --incremental false --noUnusedLocals --noUnusedParameters`, sau build | PASS, exit0 |
| Từ frontend: `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm run test:e2e` | **4/4 PASS,12.4s**, exit0; latest production build |
| Static JSX inventory | className/id/placeholder attributes của PassengerDetails +3 extracted views khớp baseline screen; không phải visual screenshot comparison |
| `git diff --check -- frontend` | PASS |
| package.json/pnpm-lock.yaml | SHA256 khớp task-start snapshot, không dependency changes |
| Backend Maven | NOT RUN: không thay backend, schema, business rules hoặc backend CI |

Một lượt extra unused check chạy đồng thời với production build gặp TS6053 vì `.next/types` đang được tái tạo. Không sửa tsconfig để né lỗi; rerun tuần tự sau build PASS. Khi verify local, không chạy build và tsc đọc generated Next types đồng thời.

Logs: `/tmp/flight-architecture-phase{1,2,3}-{tests,typecheck}.log`, `/tmp/flight-architecture-final-{lint,build,unused,e2e}.log`. Tổng80 tests gồm19 React DOM/jsdom component tests; browser4 tests là suite riêng.

Browser smoke dùng Chrome thật, local production server127.0.0.1:3100, mọi API đều mock và requests ngoài localhost bị chặn. Các flows: guest search/passenger/seat/login continuation, explicit booking/QR/F5/status-driven success/history/tickets/logout; cancellation; mất response create rồi resume history không create lần hai; mobile sign-in/search. Không thanh toán hoặc gọi backend/provider/database.

## Findings đã xử lý và giới hạn

- Đã sửa lỗi guardian string/number tại HTML boundary; kiểm chứng bằng DOM interaction cho adult index0/1.
- Đã bỏ dependency type từ lib vào HomePage, bỏ duplicate unused API types và type rõ dữ liệu qua các bước checkout.
- Đã tách form/controller/validation/builders và HTTP/auth/domain responsibilities; facade và các bảo vệ session/unknown outcome giữ nguyên.
- Còn38 lint warnings: một số any ngoài checkout props, legacy session parser, effect-driven restoration và hooks/image recommendations. Không tắt hoặc hạ thêm rules để lấy PASS.
- Backend calendar để tính tuổi quanh nửa đêm, document-only deletion chưa có API, hosting cookies/CORS, real payOS signature/webhook và cross-stack E2E vẫn là các giới hạn đã ghi trong FRONTEND_COMPLETION.md. Task này không xác minh hoặc thay các business rules đó.
- Không chạy lại security audit vì không có dependency changes; không dùng kết quả audit cũ làm bằng chứng mới.
- Không xác minh pixel-perfect screenshots, full accessibility hoặc mọi mobile checkout scenario. Browser smoke với mocks không chứng minh backend transactions/concurrency/idempotency.

Frontend tiếp tục phù hợp portfolio/demo. Có thể viết README dựa trên architecture và evidence tại đây, ghi rõ mock smoke/cross-stack limitations; không cần thêm đợt chuyển folder hoặc state library.
