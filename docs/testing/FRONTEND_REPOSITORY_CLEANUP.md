# Frontend Repository Cleanup — 2026-10-30

## Phạm vi và Git state

HEAD `4a2fe48`, branch `main`. Kiểm tra frontend/ và instruction files tại repository root. Working tree có nhiều thay đổi từ các phase trước; cleanup không reset, gom commit hoặc stage những thay đổi đó. Snapshot đầu task: `/tmp/flight-frontend-cleanup-baseline`.

Không chỉnh business logic, backend APIs, UI hoặc architecture; không cài dependency, commit/push/deploy, gọi provider/database. File CSS và mọi application/test/assets còn lại được đối chiếu SHA256 với snapshot, không thay đổi. Ngoại lệ là2 UI primitive files xác minh không có consumer rồi xóa.

## KEEP

| File/nhóm | Bằng chứng và quyết định |
| --- | --- |
| Root AGENTS.md | Giữ toàn bộ project rules; bổ sung frontend guidance dùng chung: đọc installed Next docs từ frontend/, canonical package manager/template, type generation và ownership instruction files. Không ghi test counts hoặc checklist tạm vào instructions |
| Root CLAUDE.md — mới | Một import `@AGENTS.md`, target tồn tại. Là entry point Claude dùng chung project rules, thay wrapper dưới frontend/ |
| frontend/AGENTS.md | Chỉ chứa managed Next block. Installed `next/dist/server/lib/generate-agent-files.js` tạo lại khi Next dev nhận diện agent. Xóa file không giúp giữ working tree sạch. Đồng bộ bằng installed generator, `hasCurrentAgentRules(frontend)=true` |
| environment.example | Template trackable duy nhất cho frontend/.env.local. Cùng hai biến với bản duplicate; có HTTPS/backend cookie/CORS/payOS callback guidance. Root .env* ignore không chặn tên này |
| pnpm-workspace.yaml | Có `pmOnFail: ignore` và `minimumReleaseAgeExclude` cho Next. Đây là pnpm settings thực sự, không phải file rỗng chỉ dùng monorepo. Xóa sẽ đổi installer policy |
| components.json | shadcn CLI đọc file qua getConfig; stylesheet `app/globals.css` và aliases hiện có. Alias hooks là vị trí cho scaffolding, không phải một missing runtime import |
| vercel.json | Deployment install/build commands pin pnpm12.3.4 và frozen lockfile. Framework tự discover config, không cần import từ source. Không kiểm tra hosting dashboard overrides |
| package.json, pnpm-lock.yaml | Scripts/manifest và dependency graph canonical. Hash không đổi, không tạo package-lock mới |
| next.config.mjs, postcss.config.mjs, tsconfig.json, eslint.config.mjs | Framework/compiler/tooling entry points. Next rewrite, Tailwind PostCSS, aliases và lint config còn dùng |
| playwright.config.ts, e2e/, tests/ | Playwright và Node test discovery/scripts còn dùng; không xóa tests chỉ vì không có application import |
| app/layout.tsx, app/page.tsx, app/payment/[result]/page.tsx, globals.css | Framework route/layout/style entry points; không phải dead files dù không có static consumer thông thường |
| components/ui/button.tsx, card.tsx; lib/utils.ts | Button/Card có consumers. Các class-name helpers vẫn được dùng, không đổi cn dependencies hoặc UI primitives đang hoạt động |
| public/airlines/VN.svg, VJ.svg, QH.svg | Flight cards/itinerary/tickets tạo URL `/airlines/${code}.svg` từ airline data; backend demo roster gồm các airline tương ứng. Không dùng literal-reference count để xóa dynamic assets |
| types/api.ts và model/helper modules còn lại | Canonical DTO file và các modules có consumers. Duplicate types/index.ts đã được xóa trong task trước; không phát sinh xóa DTO mới. Không purge các exported contract types chỉ vì một màn hình chưa dùng |

Theo [pnpm settings documentation](https://pnpm.io/settings), pnpm-workspace.yaml là một nguồn cấu hình package manager. Theo [Claude memory documentation](https://code.claude.com/docs/en/memory#import-additional-files), `@path` import được resolve tương đối với file khai báo. Root wrapper giữ project instructions dùng chung; chưa mở một phiên Claude/Codex mới để xác minh runtime instruction loading.

## REMOVE

| File | Kiểm tra trước khi xóa |
| --- | --- |
| frontend/CLAUDE.md | Chỉ có `@AGENTS.md`, không chứa quy tắc riêng. Root CLAUDE.md mới import root instructions, trong đó frontend guidance tương đương đã được đưa vào. Frontend managed instructions vẫn giữ cho Codex/Next tooling |
| frontend/.env.local.example | Bản ignored local duplicate, hai assignments trùng environment.example; không được source/tests/tooling/CI/deployment đọc. Những references cũ trong status là lịch sử; không phải consumer. Đã lưu snapshot trước khi xóa; không đụng real .env.local |
| frontend/components/ui/select.tsx | Không có resolved consumer theo TypeScript module resolution, repository import/export/dynamic-reference/tooling/test checks; chỉ tự định nghĩa/export Select primitives |
| frontend/components/ui/popover.tsx | Tương tự Select; không có consumer hoặc config yêu cầu file cụ thể này |

Không xóa dependency `@base-ui/react`/`cn`: Button/Card vẫn dùng các package đó. Không xóa logo airline. Các deleted files từ các task cũ như package-lock/types-index không được tính là cleanup mới.

## IGNORE — giữ local, loại khỏi Git tracking

| Artifact | Ignore rule / hành động |
| --- | --- |
| .next/ | Existing `/.next/`; không có tracked entry, không xóa cache |
| node_modules/ | Existing `/node_modules`; không có tracked entry, không xóa/reinstall dependencies |
| test-results/ | Existing `/test-results/`; không có tracked entry, giữ local artifacts |
| playwright-report/ | Existing `/playwright-report/`; không có tracked entry |
| tsconfig.tsbuildinfo | Thêm `*.tsbuildinfo`; trước đây đang tracked. `git rm --cached` để untrack, file local còn nguyên |
| next-env.d.ts | Thêm `/next-env.d.ts`; trước đây đang tracked. `git rm --cached` để untrack, file local còn và được Next regenerate |
| .DS_Store, .env.local | Existing ignore rules; không dọn cache/real environment ngoài phạm vi cần thiết |

Đã kiểm tra bằng `git check-ignore -v` và `git ls-files`. Ignore rule không tự bỏ tracked files khỏi index, nên hai generated files được untrack có chủ đích. **Index đầu task trống; cuối task chỉ có hai staged deletions của next-env.d.ts và tsconfig.tsbuildinfo.** Cả hai vẫn tồn tại local. Không stage code/docs/changes khác và không commit.

Installed Next docs (`next/dist/docs/01-app/03-api-reference/05-config/02-typescript.md` và CLI guide) khuyến nghị không track next-env.d.ts và chạy next typegen trước typecheck. Vì vậy frontend CI giữ nguyên các gates, chỉ đổi TypeScript step thành:

```sh
pnpm exec next typegen --webpack && pnpm typecheck
```

CI đã có BACKEND_API_URL HTTPS placeholder. Typegen tải Next config ở production phase nên cần cấu hình môi trường hợp lệ; không dùng backend thật. Local có thể generate types bằng next dev/build; nếu dùng typegen riêng, cung cấp environment phù hợp. Không sửa package scripts, backend CI hoặc tsconfig để bỏ type checks.

## REVIEW MANUALLY — không tự sửa

- `app/layout.tsx` khai báo `/icon-light-32x32.png`, `/icon-dark-32x32.png`, `/icon.svg`, `/apple-icon.png`, nhưng public/ không có các file đó. README cũng tham chiếu `frontend/public/favicon.ico` không tồn tại. Đây là missing/stale references, không phải file dư có thể xóa. Cần task assets/metadata riêng; cleanup này không chỉnh UI hoặc README.
- Giá trị pmOnFail/minimumReleaseAgeExclude cần quyết định package-manager policy nếu muốn thay. Không coi config không có runtime import là unused hoặc âm thầm đổi release-age/security policy.
- Actual GitHub Actions, Vercel Root Directory/dashboard overrides và real Claude/Codex session loading không được chạy trong cleanup. File/config checks không chứng minh deployment/instruction runtime đã PASS.
- Còn38 lint warnings từ source hiện có; không refactor logic hoặc hạ rules để giảm số cảnh báo trong cleanup.

## Verification thực tế

Lệnh từ frontend/ trên dependency tree pnpm đã có, npm chỉ chạy package scripts:

| Check | Kết quả |
| --- | --- |
| `npm test` | **80/80 PASS**, failed/cancelled/skipped0 |
| `NEXT_TELEMETRY_DISABLED=1 BACKEND_API_URL=https://backend.example.invalid/api/v1 node node_modules/next/dist/bin/next typegen --webpack`, rồi `npm run typecheck` | PASS, exit0 |
| `npm run lint` | PASS, exit0; **0 errors/38 warnings** |
| `NEXT_TELEMETRY_DISABLED=1 BACKEND_API_URL=https://backend.example.invalid/api/v1 npm run build` | PASS, exit0; Next16.4.1/Webpack; routes `/`, `/_not-found`, `/payment/[result]` |
| Fresh source copy | `/tmp/flight-frontend-cleanup-fresh` ban đầu không có .next hoặc next-env.d.ts, dùng symlink local node_modules. Typegen với HTTPS placeholder rồi typecheck PASS; không phải fresh dependency install hoặc actual GitHub run |
| Frontend workflow YAML parse | PASS; backend workflow không đổi |
| Ignore/tracked-artifact check | PASS; các artifact đã liệt kê ignored và không còn tracked; local files/dependencies vẫn tồn tại |
| Remaining source/test/assets hashes | PASS; tất cả không đổi trừ2 removed unused primitives. Manifest/lock/workspace/components/Vercel/environment template không đổi |
| Focused whitespace diff check | PASS |
| Browser E2E / backend Maven / security audit | NOT RUN trong cleanup; không dùng PASS của task trước làm evidence mới |

Logs: `/tmp/flight-frontend-cleanup-{tests,typegen,typecheck,lint,build}.log`, `/tmp/flight-frontend-cleanup-fresh-{typegen,typecheck}.log`. Snapshot chỉ ở /tmp, không thêm vào repo. Không gọi backend/provider/database.
