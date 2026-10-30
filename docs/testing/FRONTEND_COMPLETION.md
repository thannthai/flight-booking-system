# Final Frontend Completion — 2026-10-29

## Phạm vi và kết luận

HEAD kiểm tra: `4a2fe48`, branch `main`. Thay đổi frontend còn local/uncommitted. Đọc AGENTS.md, status và source/contract hiện tại; không coi các checkpoint cũ là bằng chứng PASS mới. Final Frontend Audit nằm trong context trao đổi trước, không tìm thấy một file báo cáo riêng cùng tên trong repository.

Đã thực hiện tuần tự F15 → quality gates → browser smoke → final verification. Frontend đủ để viết README/CV dưới dạng portfolio/demo có kiểm thử, với giới hạn tích hợp được công bố bên dưới. Không kết luận sẵn sàng vận hành production chỉ dựa trên các tests này.

Không thay backend, API contract, migrations, backend CI hoặc business rules; không commit/push/deploy, chuyển tiền, gọi provider hoặc truy cập database production. Giữ nguyên các thay đổi có trước, bao gồm README/api-plan và các file bị xóa từ task cũ.

## Phase 1 — Passenger validation (F15)

Đối chiếu trực tiếp:

- `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java`: `validatePassengerAgeAtDeparture`, `validatePassengerDocument`, `validateInfantsAndGuardians`, `validatePassengerDocumentsUniqueness`, `validateNoDuplicateActiveBookingsForPassengers`.
- `backend/src/main/java/com/thanh/backend/booking/dto/request/PassengerRequest.java`: kiểu dữ liệu/constraints transport.
- `backend/src/main/java/com/thanh/backend/booking/service/PassengerService.java`: tạo/cập nhật/xóa danh bạ và giấy tờ.

Thay đổi tập trung:

- `frontend/lib/passenger-validation.ts`: tính tuổi bằng ngày sinh và ngày khởi hành chuyến đầu, đúng mốc 2/12 tuổi và leap day; không dùng số mili giây hoặc năm hiện tại. Ngày tham chiếu là calendar trong timestamp trả về, tương ứng `OffsetDateTime.toLocalDate()` của backend.
- `PassengerDetails.tsx`: form, quickfill và directory selection dùng cùng ngày tham chiếu. Adult bắt buộc type+number; child/infant được bỏ giấy tờ. Không gửi type rỗng kèm number hoặc type có giá trị kèm number trống. Không tự ép citizen ID chỉ gồm số, passport expiry hay quy định quốc tế backend chưa có.
- Duplicate giấy tờ của ADULT/CHILD chuẩn hóa trim+uppercase và document type; không cấm trùng tên/ngày sinh vì backend không có quy tắc đó. Mỗi infant có một adult guardian riêng; nếu infant có document thì phải khớp guardian. Duplicate active booking trên cùng flight vẫn do backend từ chối qua `PASSENGER_ALREADY_BOOKED_ON_FLIGHT`; frontend không suy đoán bằng dữ liệu history của riêng một user.
- `PassengerDirectoryModal.tsx`: backend giữ giấy tờ cũ nếu update bỏ/trống type/number. UI chặn thao tác xóa riêng số giấy tờ với thông báo rõ; vẫn cho thay số hoặc xóa traveler khỏi danh bạ qua API hiện có. Sau update, refetch danh sách canonical thay vì hiển thị partial response như đã xóa giấy tờ. Xóa traveler có vé được backend detach khỏi danh bạ, không xóa lịch sử vé.
- `frontend/tests/passenger-validation.test.cjs`: 11 tests về sinh nhật/leap day, DOB không hợp lệ, giấy tờ optional, duplicates, infant/guardian và document clearing. Thêm 2 tests tương tác directory trong `components.test.cjs` ở final verification.

Checkpoint trước Phase 2: **65/65 tests PASS**, typecheck PASS. Không thay backend để diễn giải ngày theo timezone sân bay; giới hạn này được giữ rõ bên dưới.

## Phase 2 — Quality gates

`frontend/package.json` khai báo `packageManager: pnpm@12.3.4`; `frontend/pnpm-lock.yaml` là lockfile duy nhất. Không khôi phục package-lock. npm chỉ bootstrap pnpm hoặc chạy script trên dependency tree đã cài bằng pnpm, không resolve/cài dependency bằng npm.

- `frontend/eslint.config.mjs`: flat config của `eslint-config-next` 16.4.1, ESLint 10.13.0. Các lỗi correctness/hooks vẫn là error. `no-explicit-any` và `set-state-in-effect` giữ mức warning cho dynamic forms/restoration cũ để không mở refactor rộng. CJS require chỉ được cho phép trong test harness.
- Dọn unused imports/handlers/calculations, JSX entities và internal link. Các form đã được outer wrapper bảo vệ null không còn early return làm hooks có điều kiện. Callback ref cập nhật trong layout effect thay cho render.
- Nối control xác nhận thủ công sau khi kiểm tra booking history vào guard unknown-create đang có. Không tự retry createBooking khi request trước có thể đã commit. Browser test mất response xác minh không có create thứ hai khi resume từ history.
- `.github/workflows/frontend-ci.yml`: Node24, pinned pnpm12.3.4, frozen install → tests → typecheck → lint → production audit → production build. Backend workflow giữ nguyên `./mvnw clean verify`.
- Build script chủ đích `next build --webpack`: môi trường hiện tại đã gặp Turbopack/PostCSS IPC port bind denied. Dùng cùng script ở local/CI/hosting, không đổi framework. Không tuyên bố Turbopack PASS.

Checkpoint trước Phase 3: **65/65 tests PASS**, typecheck PASS, lint exit0 với **0 errors/52 warnings**, production build Webpack PASS. Workflow YAML parse PASS; chưa có GitHub Actions run thực tế.

## Phase 3 — Browser smoke thực tế

Thêm tối thiểu `@playwright/test` 1.64.1, `frontend/playwright.config.ts`, `frontend/e2e/booking.spec.ts`, script `test:e2e`; ignore artifacts. Chạy Chrome đã có trên máy, không cần tải browser. Config dùng production server local `127.0.0.1:3100`; không dùng dev server. Các máy khác có thể dùng Playwright Chromium đã cài hoặc chỉ định executable path qua biến môi trường.

**4/4 browser tests PASS** trên bản production build cuối, 7.4s:

1. Guest flight search → passenger → auto seat selection → login continuation → explicit createBooking → PNG QR từ payload → F5 → check status → history/e-ticket → logout. Counters xác minh một hold, một createBooking và một create-link qua reload.
2. Explicit cancellation → bỏ QR → history CANCELLED, một cancel request.
3. Create đã lưu ở mock nhưng mất response → guard tồn tại qua reload → resume từ history → QR, vẫn chỉ một createBooking.
4. Mobile viewport 390×844: sign-in và flight search/DEMO result truy cập được.

Đây là **browser thật với API mock**, không phải jsdom và cũng không phải cross-stack E2E. Tất cả `/api/v1` bị intercept; requests ngoài localhost bị chặn. Account, flight, tickets, token và QR payload đều chỉ dùng cho test; QR không chứa thông tin chuyển tiền thật. Thay state mock thành paid chỉ giả lập kết quả đã lưu phía server; không xác minh webhook signature, provider hoặc phát hành vé trong PostgreSQL.

Lần chạy sandbox đầu NOT RUN vì server listen EPERM. Lần chạy local được cho phép và lần rerun cuối đều PASS. Không tiếp xúc backend/database/provider. Browser smoke hiện chưa được thêm vào CI; workflow yêu cầu quality gates ở Phase 2 đã đủ, browser installation/CI execution chưa xác minh.

## Phase 4 — Verification cuối

Các lệnh script thực tế chạy từ repository root, trên dependency tree đã frozen-install bằng pnpm:

| Check | Lệnh/kết quả |
| --- | --- |
| Frozen install | Từ `frontend`: `CI=true npm exec --yes --package=pnpm@12.3.4 -- pnpm install --frozen-lockfile --store-dir /tmp/flight-frontend-pnpm-store` — PASS, không bỏ install scripts |
| Regression | `npm --prefix frontend test` — **67/67 PASS**, failed/cancelled/skipped0; gồm16 React DOM/jsdom component tests |
| TypeScript | `npm --prefix frontend run typecheck` — PASS |
| Unused TypeScript | Từ `frontend`: `./node_modules/.bin/tsc --noEmit --incremental false --noUnusedLocals --noUnusedParameters` — PASS |
| Lint | `npm --prefix frontend run lint` — exit0, **0 errors/52 warnings** |
| Build | `NEXT_TELEMETRY_DISABLED=1 BACKEND_API_URL=https://backend.example.invalid/api/v1 npm --prefix frontend run build` — PASS, Next16.4.1/Webpack; upstream placeholder không được gọi |
| Production audit | Pinned pnpm `audit --prod --json` — exit0, **0 advisories** |
| Full audit | Pinned pnpm `audit --json` — exit1, **1 LOW**, không critical/high/moderate |
| Browser | Từ `frontend`: `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm run test:e2e` — **4/4 PASS** trên latest build |
| Diff/workflow | `git diff --check -- frontend .github/workflows/frontend-ci.yml` và YAML parse — PASS |
| Backend Maven | **NOT RUN**: backend diff rỗng; không có cross-stack tests trong đợt này. Backend CI vẫn giữ nguyên |
| Hosting/real GitHub run | **NOT VERIFIED**, không deploy/push để kiểm tra |

Chạy lại thông thường với pnpm đã cài: `cd frontend`, `pnpm install --frozen-lockfile`, `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm build`, `pnpm test:e2e`. Browser smoke cần build trước và Chromium/Chrome local. CI hiện không chạy `test:e2e`.

Bằng chứng local (không commit artifacts): `/tmp/frontend-completion-final-{tests,typecheck,lint,build}.log`, `/tmp/frontend-completion-e2e-final.log`, `/tmp/frontend-completion-final-{prod,full}-audit.json`. Full-tree whitespace check vẫn gặp README whitespace có trước; focused diff PASS.

## Findings/rủi ro còn lại

Không thấy BLOCKER/HIGH mới trong phạm vi frontend đã kiểm thử; đây không phải bảo đảm cho các phần tích hợp chưa chạy.

| Mức | Phần còn lại | Quyết định |
| --- | --- | --- |
| MEDIUM | Backend tính tuổi theo calendar trong OffsetDateTime, có thể khác ngày địa phương sân bay quanh nửa đêm nếu dữ liệu được chuẩn hóa UTC | Frontend cố ý khớp backend; cần thống nhất business rule riêng trước khi đổi cả hai. Flight display vẫn dùng timezone sân bay |
| MEDIUM — verification gap | Hosting cookie/rewrite/CORS, real backend flows, payOS/signature/webhook chưa được kiểm thử end-to-end | Checklist staging dưới đây; không gán PASS từ mock |
| MEDIUM/LOW | Legacy dynamic-form `any`, effect restoration và một số hook/image recommendations:52 warnings | Giữ hiển thị warning; không mở đợt refactor component/state lớn trong task này |
| LOW | Full audit: dev-only deepmerge4.3.1 qua shadcn/@shadcn/registry, GHSA-gcqx-w48m-w2h4, chưa có patch theo audit response | Không dùng CLI/build registry/config không tin cậy; theo dõi patch. Production audit0 không loại trừ supply-chain risk |
| LOW — contract limitation | API chưa hỗ trợ xóa riêng giấy tờ, update omission giữ nguyên | UI chặn thao tác gây hiểu nhầm; không thêm endpoint hoặc đổi backend |
| LOW — coverage gap | Full accessibility, mobile toàn bộ checkout/print và cross-browser chưa xác minh; result-list leg selection tạm chưa persist đầy đủ | Manual checks trước demo; không redesign, không tuyên bố coverage rộng hơn4 smoke tests |
| LOW — documentation | README/API docs còn nội dung lịch sử, real frontend GitHub run chưa xác minh | Đợt README tiếp theo sử dụng kết quả và giới hạn tại đây; không ghi full-stack/payment-production PASS |

## Checklist kiểm thử trên deployment thực tế

Chưa thực hiện các mục này. Chỉ tạo/hủy booking tự động trên local/test hoặc staging có **database test riêng và payment mock**. Không dùng database production hoặc chuyển tiền để hoàn thành checklist.

- [ ] Hosting Root Directory=`frontend`, pinned pnpm/frozen install và Webpack build đúng; `BACKEND_API_URL` HTTPS có `/api/v1`. Backend `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGINS` đúng frontend origin, `PAYOS_RETURN_URL`/`PAYOS_CANCEL_URL` trỏ `/payment/success`/`payment/cancel`.
- [ ] HTTPS browser login/refresh/F5/logout/login account B: HttpOnly refresh cookie được gửi qua rewrite với path thích hợp; không lưu access token vào localStorage/sessionStorage; không lộ directory/history/draft của account A. Kiểm tra expired session và tab thứ hai.
- [ ] Search domestic/international, one-way/round-trip, filters/reset/pagination/empty/error; giờ SGN/SIN/ICN và chuyến quanh nửa đêm đúng timezone hiển thị. Kiểm tra sinh nhật đúng2/12 tuổi theo backend calendar đã ghi ở trên.
- [ ] Guest chọn ghế rồi login vẫn tiếp tục đúng; hold expired/seat conflict buộc tải trạng thái mới; back/reload không tạo booking ngầm. Giá cuối lấy backend, CHILD75%/INFANT10%, tax/add-ons và hai chặng đúng contract.
- [ ] QR hiển thị từ payload/checkout backend, không có số tài khoản giả. Chỉ kiểm tra nội dung QR, **không chuyển tiền**. F5/status/retry không gọi lặp createBooking/create-link; request create mất response phải kiểm tra history trước khi tự quyết định thử lại. Chỉ giả lập lỗi mạng trên test environment.
- [ ] Callback query tự thay `status=PAID` không thể tự xác nhận đã trả tiền; UI phải đọc trạng thái backend. Gateway mock đổi trạng thái pending/paid/cancelled/expired/refund; QR mất ở terminal state.
- [ ] History/tickets đúng cả hai chặng, không đếm trùng hành khách; chỉ hủy các test booking được phép. Dùng hai test accounts kiểm tra ownership/forbidden, không đọc booking của account khác.
- [ ] Directory sửa số/refetch đúng; xóa riêng số bị chặn rõ; remove traveler không phá lịch sử vé. Keyboard focus/Escape, mobile toàn bộ checkout, desktop và print đọc được; e-ticket ghi đúng tính chất demo.
- [ ] Real backend concurrency/rollback/webhook idempotency dựa trên backend tests riêng, không suy ra từ browser mocks. payOS sandbox/provider signature và cross-stack integration vẫn NOT VERIFIED cho tới khi chạy kiểm thử chuyên biệt được cho phép.

Sau các bước staging thiết yếu, có thể ghi demo URL và kết quả kiểm thử thực tế trong README. CV có thể mô tả Next.js/Spring Boot, API integration, regression tests và Playwright mock smoke; không ghi real payment hoặc production E2E nếu chưa kiểm chứng.
