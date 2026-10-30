# Phase 1 Audit — Flight Booking Backend

Ngày audit: **2026-10-26**, múi giờ `Asia/Ho_Chi_Minh`.
Phạm vi: working tree hiện tại trên nhánh `main`, HEAD `e783773`.
Lượt audit ban đầu không thay đổi application source, tests, migrations, dependencies hoặc configuration. Follow-up F01 được ghi riêng bên dưới.

**Cập nhật sau audit — F01, 2026-10-26 21:34 +07:00:** đã thực hiện RED → GREEN → REGRESSION theo yêu cầu riêng của người dùng. F01 về DB expiry tại final claim đã sửa và verify; xem mục H và `docs/PROJECT_STATUS.md`. Các số liệu 17/51 ở baseline bên dưới thuộc audit trước sửa; regression hiện tại gồm 18 Phase 1 IT. Phần follow-up này có thay đổi repository query và thêm một IT; không còn là thao tác audit-only ban đầu. F02–F10 chưa được xử lý bởi task F01.

**Mốc F02, 2026-10-26 23:34 +07:00:** tái hiện hai lịch stale writer tạo2 active bookings/seat sau F01, sửa hold acquisition bằng DB conditional UPDATE, clean verify57/57 PASS. Chi tiết lịch cũ/mới ở I.

**Cập nhật mới nhất — F03, 2026-10-27 01:02 +07:00:** 5 race trên production cũ đã RED; server-generated UUID lease và Lua compare-and-delete/expire làm GREEN. Có11 F03 IT; backend `clean verify` **68/68 PASS** (33 unit/web +35 IT), F01/F02 vẫn PASS. API/schema/TTL/payment deadline không đổi; F04 và các gap khác vẫn mở. Xem J để hiểu phạm vi lease identity, rollout legacy và các edge cases chưa chứng minh.

**Follow-up fixed deadline — 2026-10-27:** người dùng chốt không gia hạn live hold khi retry; semantics renew trong I/J là lịch sử, được thay bởi K. RED8/8 failures trước sửa → GREEN8/8; regression mở rộng27/27. **Full clean verify79/79 PASS**, Surefire33 + Failsafe46, failures/errors/skipped0 (14:01:35 +07), chi tiết tại K; F04 vẫn OPEN.

**Follow-up F04 — 2026-10-27:**8 deterministic failures trên source trước sửa, sau đó GREEN và bổ sung coverage. Booking Redis consume chuyển sau confirmed commit; hold rollback cleanup theo transaction outcome, guarded bởi fresh DB row lock + exact token, executor queue hữu hạn. Pool2 test phát hiện và sửa connection starvation của phương án synchronous callback. Xem **L** cho evidence và giới hạn TTL/crash/UNKNOWN outcome; những dòng F04 OPEN trong các mốc cũ là lịch sử.

## A. Executive Summary

**Phase 1 chưa đủ điều kiện tuyên bố hoàn thành.** Tại audit ban đầu, cả 3 nhóm đã có integration tests và chạy thành công với PostgreSQL/Redis thật: **17/17 test baseline PASS**. Follow-up F01 nâng regression lên18/18 (mục H), nhưng cả 3 nhóm vẫn còn khoảng trống so với toàn bộ yêu cầu. Không dùng tỷ lệ test PASS để suy ra 100% business invariants đã được chứng minh.

| Nhóm | Kết quả chạy riêng tại audit ban đầu | Mức hoàn thành theo yêu cầu audit ban đầu |
| --- | --- | --- |
| 1.1 SeatHoldIT | 3 PASS, 0 failures/errors/skipped | Một phần: hold cơ bản, batch failure, cạnh tranh 20 thread đã verify; expiry/release/desynchronization còn thiếu |
| 1.2 BookingConcurrencyIT | 2 PASS, 0 failures/errors/skipped | Một phần: happy path và đúng lịch xen kẽ Bob lấy hold trước Alice claim đã verify; expiry tại claim và lịch cạnh tranh khác chưa được chứng minh |
| 1.3 BookingInvariantsIT | 12 PASS, 0 failures/errors/skipped | Các scenario chính A–D đã có và pass; assertions persisted state, normalization, invalid indexes và collision tại INSERT còn thiếu |

Hai lần chạy tổng hợp **`./mvnw verify` và `./mvnw clean verify` đều có 51 PASS, 0 failures, 0 errors, 0 skipped**, exit code 0; gồm 33 Surefire test và 18 Failsafe IT (17 test Phase 1 + 1 context smoke test). Clean build compile lại 131 production source files và 8 test source files. Đây là kết quả mới của audit, không lấy kết quả session trước làm bằng chứng; clean build vẫn dùng dirty working tree, không tương đương clean Git checkout.

Các kết luận chính:

1. **Race đã báo cáo được chặn trong working tree hiện tại, trong đúng scenario test đang dựng.** Alice nhận `SEAT_NOT_AVAILABLE`, không có Booking/Ticket/Passenger của Alice được commit; DB và Redis giữ owner Bob. Có SQL conditional update thật và assertions JDBC thật. Tuy nhiên, test sửa behavior `BookingLegRepository.save` bằng spy và mô phỏng expiry bằng cập nhật DB/xóa key, nên kết luận có giới hạn.
2. **F01 tại baseline audit:** conditional update chưa kiểm tra `held_until` tại thời điểm claim. **Đã sửa trong follow-up H:** thêm predicate dùng DB wall-clock, có RED evidence và GREEN/18-test regression. Redis chỉ được kiểm tra ở validation như trước; không coi F03/F04 đã được sửa.
3. **F02 từ risk thành bug Critical đã tái hiện, rồi được sửa:** hai case AVAILABLE-read/expired-hold-read cho Bob ghi đè BOOKED và tạo booking thứ hai trên code sau F01. Follow-up I bổ sung hold CAS, giữ managed entity không dirty; cả hai GREEN và full verify57/57. Lịch nguyên bản dùng hold Alice đã expired bị F01 chặn; không dùng lịch đó làm bằng chứng bug mới.
4. **Redis không rollback cùng DB.** Failure muộn ở booking nhiều ghế có thể để DB rollback nhưng key ghế đã xử lý trước đó bị xóa. Chưa có regression test cho trường hợp này.
5. **Bằng chứng local chưa đi vào CI:** các IT, base fixture, Failsafe configuration và conditional update đang untracked/modified. CI chạy `mvn clean test`, rồi `mvn package -DskipTests`, không đi tới phase `integration-test`/`verify`.

**Không có test `VERIFIED_FAIL` trong lượt audit ban đầu.** Follow-up F01 đã bổ sung test, tái hiện RED trước production fix rồi GREEN; xem H. Các đường cạnh tranh còn thiếu vẫn là `NEEDS_REVIEW`/`MISSING`. Không mở rộng sang payment concurrency Phase 2, không gọi payment provider thật.

### Quy ước evidence

- **VERIFIED**: đã quan sát source/config hoặc đã chạy command và có kết quả; phải nói rõ loại bằng chứng.
- **REPORTED**: mô tả từ task hoặc tài liệu cũ, chưa mặc định đúng.
- **ASSUMED**: suy luận cần kiểm chứng; không được dùng làm kết quả PASS.
- `VERIFIED_PASS` trong matrix chỉ áp dụng cho phạm vi assertion thực sự đã chạy.
- `MISSING` là không tìm thấy test tương đương; `NEEDS_REVIEW` là có evidence nhưng chưa chứng minh trọn yêu cầu. `BLOCKED` chỉ dùng khi có blocker thực tế. Không có blocker Docker còn tồn tại sau khi được phép chạy ngoài sandbox.

### Repository reconnaissance

Recent commits đã đọc:

```text
e783773 fix: expose actuator health without JWT authentication
bb7d773 chore: configure dynamic port for Render deployment
3c0abd3 fix: resolve Supabase SSL handshake and configure Redis auth
884db04 update application.yaml
21488b7 chore: make env variables explicit in yaml for Render
d47c147 build: update Dockerfile to Java 21
90343c2 build: add Dockerfile for backend deployment
e3edf54 feat: implement full e2e flight booking flow and core UI components
```

Git status khi bắt đầu có sẵn:

```text
 M README.md
 M backend/pom.xml
 M backend/src/main/java/com/thanh/backend/booking/service/BookingService.java
 M backend/src/main/java/com/thanh/backend/flight/repository/FlightSeatRepository.java
 D backend/src/test/java/com/thanh/backend/BackendApplicationTests.java
 M backend/src/test/java/com/thanh/backend/payment/service/PaymentServiceImplTest.java
 M docs/api-plan.md
 D docs/coding-rules.md
 D docs/seed_mock_data.sql
?? .codex/
?? TestJDBC.class
?? TestJDBC.java
?? backend/src/test/java/com/thanh/backend/AbstractIntegrationTest.java
?? backend/src/test/java/com/thanh/backend/BackendApplicationIT.java
?? backend/src/test/java/com/thanh/backend/booking/
?? frontend/AGENTS.md
```

Trong quá trình audit, `.codex/AGENTS.md` rỗng được thay bằng root `AGENTS.md` xuất hiện từ workspace bên ngoài thao tác audit. Đã đọc root instructions và tuân thủ audit-only/no-real-payos. Audit không tạo hoặc sửa các file này. Không tìm thấy `docs/PROJECT_STATUS.md`, progress log hoặc test plan Phase 1 riêng; tài liệu nghiệp vụ hiện có là README và `docs/api-plan.md`. Các file docs bị xóa không được khôi phục. `frontend/AGENTS.md` thuộc frontend, không chi phối báo cáo backend này.

Source layout thực tế là modular monolith `backend/src/main/java/com/thanh/backend/{auth,flight,booking,payment,airport,airline,aircraft,notification,common}`. **Không có `SeatHoldService` riêng**; hold nằm trong `BookingService.holdSeats`. Không có fixture builder dùng chung ngoài `AbstractIntegrationTest`; mỗi IT lặp lại dữ liệu airport/airline/aircraft/user/flight/seat.

Inventory test tại audit ban đầu (follow-up F01 thêm1 case vào BookingConcurrencyIT, xem H):

| File dưới `backend/src/test/java/com/thanh/backend/` | Test thực thi | Loại |
| --- | ---: | --- |
| `booking/service/SeatHoldIT.java` | 3 | DB + Redis thật, không mock |
| `booking/service/BookingConcurrencyIT.java` | 2 | DB + Redis thật, spy BookingLegRepository |
| `booking/service/BookingInvariantsIT.java` | 12 | DB + Redis thật, spy BookingRepository; 4 lớp `@Nested` |
| `BackendApplicationIT.java` | 1 | Spring context, migrations, DB + Redis thật |
| `auth/security/ActuatorSecurityTest.java` | 24 | MockMvc + Security/Actuator thật, JWT collaborators mock |
| `payment/service/PaymentServiceImplTest.java` | 8 | Mockito, không chứng minh database locking/isolation |
| `payment/scheduler/BookingPaymentCleanupSchedulerTest.java` | 1 | Mockito, không chứng minh DB rollback/seat release thật |
| `AbstractIntegrationTest.java` | 0 | Base configuration/cleanup, không phải test case |

## B. Coverage Matrix

Để đọc bảng ngắn gọn, aliases dưới đây ánh xạ đến **file path thực tế**; `:N` là line number:

| Alias | File |
| --- | --- |
| SH | `backend/src/test/java/com/thanh/backend/booking/service/SeatHoldIT.java` |
| BC | `backend/src/test/java/com/thanh/backend/booking/service/BookingConcurrencyIT.java` |
| BI | `backend/src/test/java/com/thanh/backend/booking/service/BookingInvariantsIT.java` |
| AI | `backend/src/test/java/com/thanh/backend/AbstractIntegrationTest.java` |
| BS | `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java` |
| FSR | `backend/src/main/java/com/thanh/backend/flight/repository/FlightSeatRepository.java` |
| TR | `backend/src/main/java/com/thanh/backend/booking/repository/TicketRepository.java` |
| STC | `backend/src/test/java/com/thanh/backend/booking/service/SeatTransitionConcurrencyIT.java` |
| RLC | `backend/src/test/java/com/thanh/backend/booking/service/RedisLeaseConcurrencyIT.java` |

| Group | Requirement / Invariant | Existing Test | Source/Test File | Status | Evidence | Next Action |
| --- | --- | --- | --- | --- | --- | --- |
| 1.1 | AVAILABLE → HELD; DB owner đúng | `holdSeat_Success_ValidTtlAndDbState` | SH:147; BS:94 | VERIFIED_PASS | Re-read DB status HELD/owner; response có seat ID | Giữ regression |
| 1.1 | Redis key đúng owner, có TTL dương ≤900s | Cùng test | SH:163 | VERIFIED_PASS | Redis thật `GET` và `getExpire`, 0 < TTL ≤900 | Thêm lower bound gần TTL cấu hình |
| 1.1 | Deadline DB/Redis/response thống nhất, fixed900s từ acquire | RLC fixedDeadline initial/retry/mixedBatch | RLC; K | VERIFIED_PASS | Exact absolute expiry tới millisecond; retry không reset TTL; batch response lấy min | F04/clock skew vẫn ngoài scope |
| 1.1 | Hai user tranh cùng ghế | `holdSeat_Concurrent20Threads_StrictMutualExclusion` | SH:229 | VERIFIED_PASS | 1 thành công, 19 `SEAT_HELD`, DB/Redis cùng winner trên 20 worker | Giữ test; bổ sung tranh chấp lúc expiry/commit |
| 1.1 | Batch failure không xóa hold của user khác | `holdSeats_BatchPartialFailure_PreservesOtherUserHold` | SH:186 | VERIFIED_PASS | Seat1 AVAILABLE/key absent, seat2 DB/Redis vẫn thuộc Bob và TTL dương | Thêm case đã có hold cùng user trước batch |
| 1.1 | Không tạo booking từ hold user khác | Không có test trực tiếp | BS:557 | MISSING | Code có owner checks DB/Redis, test hold từ user khác không tương đương createBooking | Thêm IT gọi createBooking bằng user không sở hữu |
| 1.1 | Reacquire sau expiry thật, rồi booking bằng lease mới | F02 stale writer variant expiredInitialHold=true | STC:95 | VERIFIED_PASS | DB deadline/Redis TTL thật hết hạn; Alice reacquire, valid claim/commit trong khi Bob giữ snapshot cũ; final chỉ1 active booking | Desync/release failures vẫn cần test riêng |
| 1.1 | Retry live hold cùng user không gia hạn | STC reuse compatibility + RLC fixedDeadline/ABA | STC; RLC; K | VERIFIED_PASS | Exact deadline/UUID giữ nguyên; old request bị fence kể cả generation mới cùng deadline; vẫn book được | Retry sau expiry có thể là hold mới; xem K |
| 1.1 | Release hold, Redis/DB lệch nhau | Không có test hold-release | BS:897; FlightService:111 | MISSING | Không có release-only API/service; cancelBooking giải phóng ghế sau booking; seat map chỉ đổi effective status | Chốt contract release/reconciliation rồi kiểm thử các trạng thái lệch |
| 1.1 | Lock/atomicity của toàn bộ DB+Redis workflow | Hold20 + STC + RLC | SH:229; STC:95; RLC:94 | NEEDS_REVIEW | DB transitions có CAS; Redis mutations compare full identity atomic; F04 delay consume/guarded rollback cleanup; DB/Redis vẫn không là một transaction | Managed outcomes verify tại L; residual crash/partition recovery còn giới hạn |
| 1.2 A | Booking PENDING, user, PNR | `createBooking_HappyPath_CompletesBookingAndUpdatesSeatState` | BC:179 | VERIFIED_PASS | Service response và committed Booking đúng status/user/regex | Giữ regression |
| 1.2 A | Deadline 15 phút | Cùng test | BC:232; BS:311 | VERIFIED_PASS | DB paymentDeadline trong before/after+15m ±5s | Có thể siết với createdAt/Clock; không đổi rule |
| 1.2 A | Pricing đúng implementation hiện tại | Cùng test | BC:240; BS:289 | VERIFIED_PASS | 1 adult: base 1,500,000; tax 150,000; discount 0; total 1,650,000 | Mở rộng child/infant/add-on khi bổ sung pricing coverage |
| 1.2 A | BookingLeg, Passenger, Document persist đủ field/link | Chỉ kiểm Ticket/FK gián tiếp | BC:250; BS:316 | NEEDS_REVIEW | Có Ticket hợp lệ nên FK tồn tại; chưa assert leg direction/order, passenger/document fields/link | Bổ sung persisted assertions trực tiếp |
| 1.2 A | Ticket PENDING và seat link | Cùng happy test | BC:251 | VERIFIED_PASS | 1 Ticket PENDING, đúng seat và ticketNo chứa PNR | Assert passenger/leg links và price đầy đủ |
| 1.2 A | Seat BOOKED, clear hold, consume Redis | Cùng happy test | BC:258 | VERIFIED_PASS | Re-read DB BOOKED, null metadata, key absent; Bob hold bị từ chối | Giữ regression, thêm rollback nhiều ghế |
| 1.2 B | Bob reclaim trước Alice claim: Alice phải fail | `createBooking_CheckThenActRace_DeterministicReproduction` | BC:277; FSR:20 | VERIFIED_PASS | Alice `SEAT_NOT_AVAILABLE`; SQL status/owner predicate thật; DB/Redis Bob còn nguyên | Giảm thay thế behavior của repository spy |
| 1.2 B | Booking transaction rollback sau khi đã insert | Cùng race test | BC:417 | VERIFIED_PASS | Raw JDBC count Booking/Ticket/Passenger của Alice =0 sau worker kết thúc | Thêm count leg/document/add-ons và case failure sau ticket đầu |
| 1.2 B | Independent transactions/connections | Cùng race test | BC:341; AI:15 | VERIFIED_PASS | Worker Alice giữ transaction; main thread commit DB expiry/Bob hold; JDBC đọc được Bob trước Alice resume; không outer `@Transactional` | Không suy ra isolation level cụ thể |
| 1.2 B | DB hold còn hạn ngay lúc final claim, kể cả sau validation/transaction start | `createBooking_HoldExpiresAfterValidation_RejectsFinalClaimAndRollsBack` | BC:448; FSR:51 | VERIFIED_PASS | Follow-up H RED/GREEN; full verify F02 chạy lại PASS, predicate clock_timestamp giữ nguyên | Giữ regression F01 |
| 1.2 B | Consume đúng lease snapshot, giữ newer same-user lease | `bookingMustConsumeValidatedLease_NotNewSameUserLease_AndRollBackOnMismatch` | RLC:290; BS:545,722 | VERIFIED_PASS | Lua so sánh identity validation; mismatch trả0, SEAT_NOT_AVAILABLE và zero graph rows; lease mới/DB hold còn nguyên | Multi-seat consume/commit failure là F04 |
| F03 | Stale cleanup/delete và same-user ABA | `staleCleanup_MustNotDeleteReacquiredLease` (2 cases) | RLC:94 | VERIFIED_PASS | RED xóa lease2; GREEN giữ raw identity và TTL, fresh JDBC HELD đúng new owner | Giữ regression generation fencing |
| F03 | Wrong actor/token, missing/expired key | RLC primitive Redis tests | RLC:242,259 | VERIFIED_PASS | Lua return false/no mutation/no resurrection; actor khác và token sai không đổi raw value/TTL | Không suy ra HTTP client fencing |
| F03 | Cancel cũ không xóa lease holder mới | `cancellationHasNoAuthorityToDeleteLeaseOfNewHolder` | RLC:334; BS:903 | VERIFIED_PASS | Cancel không có identity của key hiện tại nên không DEL; booking/ticket CANCELLED, new Bob hold/Redis intact | Orphan recovery/cancel DB concurrency là scope riêng |
| 1.2 B | Hibernate persistence context đồng bộ sau bulk update | Happy/race chỉ gián tiếp | BS:729; FSR:20 | NEEDS_REVIEW | Không clearAutomatically; caller tự set status/hold fields; happy path pass, có thêm entity UPDATE | Thêm assertion trong transaction và multi-seat path; không tự động clear toàn context tùy tiện |
| 1.2 B | Hai createBooking cùng valid hold: chỉ một thành công | `competingBookings_SameLiveHold_SecondUpdateWaitsAndRechecksStatus` | STC:184 | VERIFIED_PASS | Hai PID/transaction READ COMMITTED riêng; pg_blocking_pids xác nhận UPDATE2 chờ UPDATE1; graph1, loser rollback | Giữ regression; không suy ra mọi writer đều an toàn |
| 1.2 B | Stale hold không đưa BOOKED về HELD hoặc tạo active booking thứ hai | `staleHoldRead_BookingCommitsBeforeHoldResumes_MustNotReopenSeat` (2 variants) | STC:95; BS:150; FSR:22 | VERIFIED_PASS | RED cả2 variants có activeBookings2; GREEN hold SEAT_HELD, Bob booking SEAT_NOT_AVAILABLE, graph1/Redis absent | Giữ CAS và tránh dirty entity save sau CAS |
| 1.3 A | Hai adult cùng identity type+number bị reject | `createBooking_DuplicatePassengerDocuments_ThrowsInvalidPassengerDocumentAndRollsBack` | BI:208 | VERIFIED_PASS | `INVALID_PASSENGER_DOCUMENT`; JDBC Booking/Ticket =0 | Assert passenger/leg/docs/holds đầy đủ |
| 1.3 A | Hai document khác nhau hợp lệ | `createBooking_DistinctPassengerDocuments_Succeeds` | BI:265 | VERIFIED_PASS | Booking PENDING; JDBC count Booking=1 | Assert 2 passengers, docs, tickets và seat/Redis state |
| 1.3 A | Trim/case normalization, type khác nhau | Không có test | BS:443; TR:26 | MISSING | Source normalize same-booking bằng trim+uppercase; query active dùng lower(trim) và identity type | Test khoảng trắng/case và cùng number khác type; làm rõ canonical lưu trữ |
| 1.3 B | 1 adult + 2 infant cùng guardian reject | `createBooking_OneAdultTwoInfants_ThrowsInvalidInfantGuardian` | BI:325 | VERIFIED_PASS | Đúng `INVALID_INFANT_GUARDIAN` | Thêm zero-write/hold-state assertions |
| 1.3 B | 0 adult + 1 infant reject | `createBooking_ZeroAdultsWithInfant_ThrowsInvalidInfantGuardian` | BI:374 | VERIFIED_PASS | Đúng error; fixture đồng thời có self-reference guardian | Thêm variant không self-reference để phân biệt nhánh |
| 1.3 B | Guardian là child reject | `createBooking_ChildAsGuardianForInfant_ThrowsInvalidInfantGuardian` | BI:400 | VERIFIED_PASS | Có adult khác, guardian index trỏ Child; đúng error | Giữ regression |
| 1.3 B | 1 adult + 1 infant hợp lệ | `createBooking_ValidAdultAndInfant_Succeeds` | BI:450 | VERIFIED_PASS | Response PENDING, 2 persisted tickets | Chưa assert guardian_id, infant seat null/type/price; bổ sung |
| 1.3 B | Guardian null/negative/out-of-range/self/index mapping | Chỉ self-reference ở test 0 adult | BS:404; BI:388 | MISSING | Guard clauses có code; thiếu các case độc lập, guardian đứng sau infant | Bổ sung parameterized scenarios và persisted guardian link |
| 1.3 C | PENDING cùng flight+document chặn booking mới | `createBooking_PassengerAlreadyHasPendingBooking_ThrowsException` | BI:551; TR:18 | VERIFIED_PASS | Hai booking owners khác nhau; cùng identity → `PASSENGER_ALREADY_BOOKED_ON_FLIGHT` | Thêm no-side-effect assertions |
| 1.3 C | COMPLETED cùng flight+document chặn booking mới | `createBooking_PassengerAlreadyHasCompletedBooking_ThrowsException` | BI:587 | VERIFIED_PASS | Đúng error trên fixture DB thật | Giữ regression |
| 1.3 C | CANCELLED không chặn | `createBooking_PassengerPreviousBookingCancelled_Succeeds` | BI:623 | VERIFIED_PASS | Tạo PENDING thành công | Fixture không test hành vi cancelBooking production; bổ sung riêng |
| 1.3 C | Active trên flight khác được phép | `createBooking_PassengerActiveBookingOnDifferentFlight_Succeeds` | BI:660 | VERIFIED_PASS | Cùng document, flight2 khác flight1, tạo thành công | Giữ regression |
| 1.3 C | Hai request cùng document trên hai seat khác nhau | Không có | BS:479; TR:18 | MISSING | Exists-check không reservation/identity lock/constraint chống race tương ứng | Kiểm chứng và chọn serialization theo identity+flight |
| 1.3 D | PNR format và pre-check retry | `createBooking_PnrFormatAndRetryLogic_Succeeds` | BI:707; BS:881 | VERIFIED_PASS | Stub exists=true rồi false; regex đúng, verify ≥2 calls; booking vẫn persist DB | Chỉ chứng minh branch retry, không collision cạnh tranh tại INSERT |
| 1.3 D | PostgreSQL UNIQUE(pnr) thật | `createBooking_PostgresUniqueConstraintOnPnr_ThrowsDataIntegrityViolation` | BI:752; V1:119 | VERIFIED_PASS | `saveAndFlush` lần 2 lỗi SQLState23505, constraint `booking_pnr_key`, key DUP999 | Thêm count=1 và assert đúng constraint trong test |
| 1.3 D | Collision phát sinh sau exists-check được retry | Không có | BS:890; BS:314 | MISSING | Không catch unique violation/retry transaction ở INSERT | Bổ sung deterministic DB collision và retry policy |
| Infra | Flyway V1–V8 + Hibernate validate trên DB mới | Tất cả IT + contextLoads | AI:15 | VERIFIED_PASS | Containers khởi chạy; 8 migrations áp dụng; context start thành công | Kiểm tra PostgreSQL17/JDK21 nếu là release target |
| Infra | Maven discovery cả nested IT | `./mvnw verify`, `./mvnw clean verify` | pom.xml:201 | VERIFIED_PASS | Mỗi lần 33 Surefire +18 Failsafe; 12 nested invariant tests được chạy | Đưa verify vào CI sau review |
| Infra | CI tự động chạy Phase 1 | Không có job tới verify | `.github/workflows/ci.yml:67` | NEEDS_REVIEW | `clean test` + `package -DskipTests`; IT chưa tracked | Commit phạm vi Phase1 đã review và cập nhật CI |

Các kiểm chứng mới của F04 (chi tiết method names/evidence tại L):

| Requirement | Test group | Status | Evidence / limit |
| --- | --- | --- | --- |
| Multi-seat rollback/real commit rejection giữ leases và zero graph | BookingRedisConsistencyIT core | VERIFIED_PASS | PostgreSQL trigger/real claim, cả6 bảng + DB/Redis deadlines |
| Post-commit Redis failure không thành booking failure | DEL ACL + commit visibility | VERIFIED_PASS | Independent JDBC count1 trước DEL, attempts mọi snapshot, response success |
| Rollback cleanup giữ concurrent retry/new generation | Shared UUID adoption + NOWAIT + generation2 variants | VERIFIED_PASS | Live DB guard; real55P03; exact compare-delete giữ newer Alice/Bob |
| Pool2/queue-full không giữ caller vì cleanup | Small-pool run + fullCleanupQueue | VERIFIED_PASS | Pool starvation đã được phát hiện/sửa; deferred cleanup, bounded queue, original error/expiry |
| Crash/partition/end-to-end recovery tức thì | Reconstructed durable-state cases | NEEDS_REVIEW | DB safety/Redis original TTL đã test; chưa SIGKILL/TCP partition/durable retry guarantee |

### Business rules và schema đã đối chiếu

- Redis convention sau F03: **`hold:seat:{seatId}` → `userId:UUID`**, token server-generated cho mỗi acquire mới, renew giữ token, compare mutation bằng Lua. API/DB không mang token; service giữ request-scoped snapshots. TTL cấu hình900 giây như cũ. Trước F03 value chỉ user ID; legacy values fail closed và tự expire. API plan vẫn dùng key user suffix, thuộc docs drift F10.
- Hold DB: `flight_seats.status=HELD`, `held_by_user`, `held_until`. Expiry DB không được scheduler riêng reset tự động; `FlightService.getSeatsByFlightId` (`backend/src/main/java/com/thanh/backend/flight/service/FlightService.java:111`) chỉ trả effective AVAILABLE khi expired. `holdSeats` cho reclaim nếu DB expired và Redis cho phép. Không coi DB còn HELD sau TTL là lỗi tự thân khi đang dùng lazy expiry; vấn đề là thiếu contract/test cho trạng thái DB/Redis lệch nhau.
- Booking creation: PENDING và seat **BOOKED ngay khi tạo booking**, Ticket PENDING; không chờ webhook mới BOOKED. PaymentDeadline = thời gian lấy đầu `createBooking` +15 phút, độc lập với TTL hold đã trôi trước đó (`BS:187`, `BS:311`). Test xác minh deadline theo thời điểm gọi; chưa so trực tiếp `created_at` ở DB.
- Pricing: adult = seat.price; child = 75% seat.price; infant = 10% basePrice mỗi flight, không có seat; add-ons do server tính (baggage 200,000; meal 80,000; insurance 50,000 mỗi quantity); tax=10% tổng base gồm fare/add-ons, HALF_UP scale2; discount hiện là 0. Happy path assertion 1,650,000 là đúng implementation. Không sửa assertion vì dự đoán pricing khác.
- Document uniqueness trong cùng booking áp dụng ADULT/CHILD theo identity type + trim/case-insensitive number. Infant có document phải khớp guardian, hoặc có thể không có document. Active booking query dùng document, flight và **Booking status PENDING/COMPLETED**; không dùng user ID hoặc tên làm identity, không lọc Ticket status (`TR:18`).
- V1 `backend/src/main/resources/db/migration/V1__init.sql:119`: PNR NOT NULL UNIQUE; V1:147 tạo tickets nhưng **không unique active flight_seat_id**. Foreign keys tồn tại cho booking/legs/passenger/docs/seat (V1:216). Không có `@Version` trên FlightSeat/BaseEntity.
- V1:255–265: indexes tìm flight, `(flight_id,status)`, partial held_until, booking user, tickets booking; V3 xóa reminder_sent và sửa enum document thành CITIZEN_ID/PASSPORT; V4 thêm contact/guardian FK; V5 bỏ global unique identity_number; V6 thêm deadline NOT NULL/index, order sequence/unique order_code và partial unique một PENDING payment/booking; V7 unique `(flight_id,seat_no)`, unique `(flight_no,departure_time)`, unaccent; V8 cho passenger.user_id null. Tất cả V1–V8 đã áp dụng trong test thật. Chưa có behavioral test riêng cho mọi index/constraint; không suy ra planner sẽ sử dụng index chỉ vì index tồn tại.
- Transaction: `holdSeats`, `createBooking`, `cancelBooking` có Spring `@Transactional`, default propagation/isolation, không REQUIRES_NEW; AppException extends RuntimeException nên lỗi claim count0 làm rollback DB (`backend/src/main/java/com/thanh/backend/common/exception/AppException.java:6`). Helpers private chạy trong transaction service. Không có XA/Redis transaction synchronization/compensation sau commit. Isolation level cụ thể không được test assert; log Hibernate báo `undefined/unknown`, không mặc định coi đã verify READ COMMITTED.
- Locks: `BookingRepository.findByIdForUpdate` và `PaymentRepository.findByIdForUpdate` có PESSIMISTIC_WRITE/timeout3000; đó là payment/cleanup paths. Seat claim và, sau follow-up F02, hold acquisition đều dùng conditional UPDATE. STC đo được READ COMMITTED và row-update lock wait thật; không thêm pessimistic lock vào initial seat read. Tại baseline hold còn dùng entity UPDATE theo id; behavior này đã được thay ở I.
- Payment idempotency **đã có thiết kế trong source**, chưa được chứng minh bằng integration concurrency: reuse/reconcile PENDING intent, unique pending payment index, booking→payment row locks, webhook SUCCESS return sớm. 8 payment unit tests dùng mock; không có dedicated duplicate-webhook IT. Các kiểm chứng concurrency payment chuyên sâu vẫn thuộc Phase 2, không được coi là đã hoàn thành ở đây và không triển khai trong audit.

## C. Bug / Risk Findings

### F01 — High — Expiry không được kiểm tra lại ở atomic claim

**Trạng thái follow-up: RESOLVED / VERIFIED_PASS cho DB expiry tại final claim, 2026-10-26 21:34 +07:00.** Bản ghi finding gốc dưới đây giữ lại làm lịch sử; source hiện tại thêm `s.heldUntil > cast(function('clock_timestamp') as OffsetDateTime)` tại `backend/src/main/java/com/thanh/backend/flight/repository/FlightSeatRepository.java:30`. Test mới tại `backend/src/test/java/com/thanh/backend/booking/service/BookingConcurrencyIT.java:448` có RED trước fix và GREEN sau fix, regression 18/18. Redis ownership/consume atomicity vẫn thuộc F03/F04, không được coi đã resolve. Xem evidence H.

- **File/line:** `backend/src/main/java/com/thanh/backend/flight/repository/FlightSeatRepository.java:26`; `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java:557`, `:732`, `:743`.
- **Evidence:** VERIFIED từ source/SQL: WHERE có id/status/userId, không held_until. Validation dùng `now` lấy từ đầu request; Redis GET cũng trước thời điểm claim.
- **Actual behavior:** đường code cho phép hold qua validation, sau đó hết hạn nhưng vẫn còn DB HELD cùng owner, tiếp tục claim BOOKED. Key Redis có thể đã hết hạn mà không được kiểm tra lại.
- **Expected:** booking không được hoàn thành từ hold đã expired; deadline/ownership phải được bảo vệ tại điểm claim, với semantics rõ ràng cho Redis.
- **Reproduce đề xuất, chưa chạy:** pause sau validation; để cả Redis TTL và DB deadline hết hạn, không cho Bob acquire; resume Alice. Existing test không dựng scenario này.
- **Suggested fix:** đưa expiry predicate dùng thời gian phù hợp tại claim vào DB update, yêu cầu affected rows=1; chọn cách consume/verify Redis ownership an toàn và regression IT. Không dùng `now` cũ để vô hiệu hóa kiểm tra expiry mới.

### F02 — Critical — Rủi ro stale hold writer ghi đè seat đã BOOKED

**Follow-up 2026-10-26 23:34 +07:00: CONFIRMED_RED → RESOLVED / VERIFIED_PASS trong phạm vi hold/create transitions được kiểm thử.** Finding gốc dưới đây là lịch sử. Lịch dùng old expired Alice lease không còn khả thi sau F01; hai lịch khác với Alice có lease mới hợp lệ đã tái hiện double active booking thật. Fix: `BookingService.java:150` gọi `FlightSeatRepository.acquireHoldIfAvailable` (`FSR:22`), không mutate/save stale entity. Test `STC:95` và evidence chi tiết ở I; full verify57/57. F03/F04 không được đóng bởi kết quả này.

- **File/line:** `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java:107`, `:129`, `:150`, `:743`; `backend/src/main/java/com/thanh/backend/flight/entity/FlightSeat.java:31`; `backend/src/main/resources/db/migration/V1__init.sql:147`.
- **Evidence:** VERIFIED source + SQL log: hold/update entity chỉ có `WHERE id=?`, không status/owner/version predicate; Redis key bị xóa trước transaction booking commit; không có unique active ticket/seat constraint. **Impact là NEEDS_REVIEW, chưa tái hiện full schedule.** 20-thread hold test và race Bob-before-Alice không chứng minh an toàn lịch dưới đây.
- **Actual behavior/risk:** Redis mutual exclusion có hạn và không fence writer DB đã đọc state cũ. Một transaction hold có thể giữ entity cũ rồi flush sau writer khác.
- **Expected:** không path nào được chuyển seat BOOKED về HELD do stale hold; hai booking không thể cùng sở hữu active tickets trên cùng seat.
- **Reproduce đề xuất, chưa chạy:** Alice validate khi hold còn hạn; tới expiry Bob bắt đầu hold và đọc DB HELD cũ rồi pause trước Redis acquire; Alice claim và DEL key; Bob SETNX thành công rồi save stale entity, UPDATE chờ Alice commit; Bob ghi HELD sau Alice BOOKED. Sau đó thử Bob createBooking với document khác và kiểm tra active tickets. Thiếu expiry predicate F01 là một điều kiện trong lịch này.
- **Suggested fix:** dùng database CAS/locking nhất quán cho **hold lẫn claim**, kiểm soát expiry/owner/generation; phối hợp Redis với DB commit, thêm deterministic regression chạy production behavior và cân nhắc defense-in-depth constraint phù hợp với ticket lifecycle. Không chỉ thêm một owner check vào booking rồi kết luận invariant toàn hệ thống đã đảm bảo.

### F03 — High — Redis owner check và mutation không atomic

**Follow-up 2026-10-27 01:02 +07:00: CONFIRMED_RED → RESOLVED / VERIFIED_PASS trong phạm vi server-side lease generations.** Finding gốc dưới đây là lịch sử. 5 real-service race cases đã RED trước production changes; Lua + UUID lease làm GREEN; bổ sung đủ11 F03 IT và clean verify68/68. Actor từ authentication; cleanup/consume mang exact captured lease, không reread lease mới theo user ID. Cancel không DEL key không thuộc booking. Xem J cho legacy rollout/client-fencing limitations; F04 vẫn OPEN.

- **File/line:** `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java:140`, `:155`, `:167`, `:743`, `:923`.
- **Evidence:** VERIFIED source: GET→EXPIRE; GET→DEL trong cleanup; DEL vô điều kiện sau claim/cancel. SETNX ban đầu atomic nhưng không làm cả chuỗi operations atomic.
- **Actual behavior/risk:** key hết hạn và owner khác acquire giữa GET và DEL/EXPIRE thì code có thể xóa hoặc gia hạn key của owner mới. Value chỉ có user ID cũng không phân biệt hai lease khác nhau của cùng user.
- **Expected:** renew/release/consume chỉ mutate đúng lease còn thuộc request/owner hợp lệ.
- **Reproduce đề xuất, chưa chạy:** pause sau GET owner Alice; expire key; Bob acquire; resume DEL/EXPIRE. Chưa có test tương đương.
- **Suggested fix:** compare-owner/token-and-delete hoặc compare-owner/token-and-expire atomic, ví dụ Lua tương thích Redis7; cân nhắc unique lease token. Redis hướng dẫn chỉ release lock nếu value vẫn khớp, xem [Distributed Locks with Redis](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/). Đây không tự tạo atomic transaction xuyên DB/Redis.

### F04 — High — DB rollback không khôi phục Redis side effects

**Follow-up F04 tại L:** managed transaction failures đã được xử lý; crash/outage dùng best-effort cleanup + original TTL, không phải atomic cross-store transaction/durable recovery. Fixed-deadline task K trước đó chưa xử lý F04. Finding gốc bên dưới là evidence lịch sử.

- **File/line:** `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java:93`, `:149`, `:164`, `:686`, `:738`, `:743`.
- **Evidence:** VERIFIED source: Redis operations chạy trực tiếp; try/catch chỉ nằm trong body holdSeats, còn DB commit nằm ngoài body ở transaction interceptor. Không có afterCompletion compensation. **Multi-seat failure chưa có execution evidence.**
- **Actual behavior/risk:** createBooking nhiều seat xử lý seat1 claim+DEL, seat2 claim count0 hoặc failure muộn → DB rollback cả seat1/booking/children, nhưng Redis key1 vẫn mất. Ngược lại holdSeats commit-time failure xảy ra sau return có thể để Redis key dù DB rollback. Batch test hiện tại chỉ cover lỗi seat2 bị Bob giữ trong holdSeats body, không cover các trường hợp này.
- **Expected:** failed booking không để orphan partial records và không làm mất hold hợp lệ/hold người khác; failure handling DB/Redis phải có contract và khả năng recovery.
- **Reproduce đề xuất, chưa chạy:** hold hai seat; tạo failure muộn sau khi ticket1 được persist và Redis1 bị consume; assert toàn bộ DB + Redis sau rollback. Thêm commit-time failure path riêng.
- **Suggested fix:** thiết kế DB-authoritative transitions, Redis cleanup theo transaction outcome và reconciliation/compensation an toàn theo lease; test side effects sau rollback. Không giả định annotation `@Transactional` bao trùm StringRedisTemplate.

### F05 — High — Phase 1 chưa là regression gate trên repository/CI

**Follow-up 2026-10-29:** source/tests/pom đã tracked tại `ac8e030`; workflow được sửa local sang `./mvnw clean verify`, giữ chức năng cũ và bỏ duplicate package skipTests. Local95/95 PASS; **GitHub execution/status gate chưa được xác nhận**, không push/deploy. Xem M; finding gốc dưới đây giữ evidence audit ban đầu.

- **File/line:** `.github/workflows/ci.yml:67`, `:71`; `backend/pom.xml:128`, `:201`, `:213`; các IT và `AbstractIntegrationTest` đang untracked.
- **Evidence:** VERIFIED git status/config: Failsafe/dependencies chỉ có trong dirty pom; IT chưa tracked; CI không gọi verify. Không đọc run log CI từ GitHub, nên kết quả pipeline gần nhất là UNKNOWN.
- **Actual:** test pass local hiện tại không phải trạng thái clean checkout HEAD hoặc chứng minh CI có chạy IT. `mvn package -DskipTests` không chạy các phase sau package.
- **Expected:** thay đổi Phase 1 được review/version-control và CI thực thi đúng 17 Phase1 tests, thất bại nếu IT lỗi/mất discovery.
- **Reproduce:** đối chiếu git status/`git diff backend/pom.xml`/CI YAML; log verify audit ghi riêng Surefire33/Failsafe18.
- **Suggested fix:** sau review, commit scope Phase1 thích hợp và chuyển job validation sang lifecycle verify với Docker, bảo đảm reports được giữ lại; không dùng service containers đang chạy để suy ra Testcontainers IT tự được chạy.

### F06 — Medium — Race test thay thế repository behavior và chưa bao phủ mọi rollback assertion

- **File/line:** `backend/src/test/java/com/thanh/backend/booking/service/BookingConcurrencyIT.java:319`, `:359`, `:417`, `:429`; `backend/src/test/java/com/thanh/backend/booking/service/BookingInvariantsIT.java:250`, `:485`.
- **Evidence:** VERIFIED test code và run PASS. `doAnswer` tự persist/merge BookingLeg, không delegate save thật. Chạy persistence thật nên đây **không phải chỉ mock assertion**, nhưng cũng không phải workflow hoàn toàn nguyên vẹn.
- **Actual:** test race thật giữa transaction Alice và Bob; expiry được sửa bằng test, không phải TTL tự nhiên. Save replacement tương đương phần cơ bản của save cho fixture leg mới nhưng có thể khác lifecycle/callback/proxy behavior. JDBC assert zero Booking/Ticket/Passenger, chưa count trực tiếp legs/documents/add-ons. Các positive invariant tests thường chỉ assert response/count giới hạn.
- **Expected:** coordination chỉ pause/resume, không đổi persistence semantics; kiểm tra đầy đủ committed state, rollback và Redis side effects.
- **Reproduce:** đọc doAnswer/save replacement và các assertions; không có failure trong lần chạy audit.
- **Suggested fix:** dùng synchronization seam/delegation bảo toàn save thật; tăng assertions persisted state đúng trọng tâm. Giữ waits bounded và finally release latch. Không dùng spy để giả claim update count hoặc giả SQL locking.

### F07 — Medium — Duplicate active passenger booking vẫn là check-then-act

- **File/line:** `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java:469`; `backend/src/main/java/com/thanh/backend/booking/repository/TicketRepository.java:18`; `backend/src/main/resources/db/migration/V5__drop_passenger_documents_identity_number_unique.sql:3`.
- **Evidence:** VERIFIED source: exists-query rồi tạo booking/passenger/ticket, không identity+flight lock/reservation hoặc constraint tương ứng. Existing tests đều tạo booking cũ commit xong rồi gọi request mới.
- **Actual behavior/risk:** hai request cùng document trên cùng flight, nhưng giữ hai seat khác nhau, có thể cùng vượt exists-check. Seat CAS không serialize identity hành khách. Runtime cạnh tranh này UNKNOWN.
- **Expected:** nếu duplicate-active rule là invariant ở mọi concurrency, chỉ một request thành công, kể cả owners/seat khác nhau.
- **Reproduce đề xuất, chưa chạy:** hai independent transaction cùng identity+flight, hai seat hợp lệ; pause sau validation cả hai rồi resume, count active bookings theo document.
- **Suggested fix:** chốt scope concurrency của rule, canonical identity và cơ chế serialization/reservation theo identity+flight; thêm IT thực. Không thêm global unique document vì sẽ phá rebooking CANCELLED/different flight và directory behavior V5.

### F08 — Medium — PNR retry chỉ xử lý collision thấy trước INSERT

- **File/line:** `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java:881`, `:314`; `backend/src/test/java/com/thanh/backend/booking/service/BookingInvariantsIT.java:714`, `:785`.
- **Evidence:** VERIFIED source/test: exists-loop retry có pass; PostgreSQL unique reject có pass. Không có retry quanh insert/transaction khi một writer khác dùng PNR sau exists-check.
- **Actual behavior/risk:** constraint ngăn duplicate PNR, nhưng concurrent collision có thể làm request hợp lệ thất bại DataIntegrityViolation; không có graceful generation retry tại bước này. Không tái hiện bằng random chance, không khẳng định test mocked pre-check chứng minh DB collision handling.
- **Expected:** collision policy rõ ràng; nếu yêu cầu retry, phải bao phủ unique violation thực tế với bounded retries/transaction mới phù hợp.
- **Reproduce đề xuất, chưa chạy:** deterministic candidate chung, cả hai exists=false, cho writer1 commit rồi writer2 INSERT.
- **Suggested fix:** tách generation seam phù hợp và retry an toàn ở transaction boundary; xác định đúng constraint. Không tiếp tục dùng transaction đã rollback-only sau SQL integrity error.

### F09 — Medium — Test infrastructure còn nguy cơ cleanup/leak/flakiness

- **File/line:** `backend/src/test/java/com/thanh/backend/AbstractIntegrationTest.java:29`, `:54`; `backend/src/test/java/com/thanh/backend/booking/service/SeatHoldIT.java:238`, `:278`; `backend/src/test/java/com/thanh/backend/booking/service/BookingConcurrencyIT.java:429`.
- **Evidence:** VERIFIED source; **chưa quan sát flake** trong các lần audit. Base gọi getConnection nhiều lần không close, dùng static containers chia sẻ; cleanup flushDb/truncate toàn schema. SeatHoldIT thiếu try/finally bảo đảm shutdown nếu assertion trước shutdown thất bại; BookingConcurrencyIT có finally shutdownNow nhưng không awaitTermination sau đó.
- **Actual risk:** failure/timeout có thể còn worker khi cleanup hoặc test sau chạy; parallel execution sẽ không an toàn vì cleanup toàn DB/Redis. Fixtures dùng nanoTime modulo cho phone/registration/PNR seed nên uniqueness là xác suất, không tuyệt đối. Redis password/SSL không được DynamicPropertySource reset, có thể chịu ảnh hưởng env deployment.
- **Expected:** executor/connection luôn được cleanup; isolation dữ liệu rõ, test không phụ thuộc global env hoặc may mắn random IDs.
- **Reproduce đề xuất, chưa chạy:** ép timeout trước shutdown hoặc bật parallel tests sẽ bộc lộ nguy cơ; audit không thay config để ép lỗi.
- **Suggested fix:** try/finally + await termination, close Redis connections, deterministic unique fixtures, isolate data hoặc cấm parallel rõ ràng; pin test Redis auth/SSL settings. Không mặc định dữ liệu đã rò rỉ: full verify hiện pass tuần tự.

### F10 — Low — Tài liệu mô tả Redis key và booking lifecycle lệch source

- **File/line:** `docs/api-plan.md:130`, `:515`, `:685`; `README.md:42`; `backend/src/main/java/com/thanh/backend/booking/service/BookingService.java:129`, `:311`, `:732`.
- **Evidence:** VERIFIED đối chiếu source/docs.
- **Actual:** API plan dùng key có user suffix và mô tả BOOKED ở webhook, còn source dùng key theo seat và BOOKED khi createBooking. README liên kết hết payment window với Redis TTL; thực tế key bị consume ngay lúc tạo booking, DB deadline/scheduler quản lý pending booking.
- **Expected:** tài liệu phân biệt hold TTL trước booking và payment deadline sau booking; không dùng tài liệu cũ làm test oracle.
- **Reproduce:** đối chiếu các dòng đã nêu.
- **Suggested fix:** cập nhật docs theo implementation/business rule được xác nhận sau audit; không thay behavior để khớp tài liệu cũ.

### Ghi chú Hibernate first-level cache

Bulk update không tự đồng bộ managed FlightSeat; `@Modifying` hiện không clear context. Caller đang tự gán BOOKED/null metadata sau update thành công (`BS:745`), và happy path xác nhận state persisted đúng; log còn cho thấy entity dirty UPDATE theo id sau bulk UPDATE. **Không có bằng chứng một cache inconsistency đã làm test hiện tại sai hoặc fail.** Đường claim0 throw trước các setter; transaction rollback và JDBC đọc Bob đúng. Rủi ro nằm ở coupling giữa bulk SQL/manual setters và các writer khác, cần test khi mở rộng. Spring Data cũng giải thích context có thể giữ entity cũ sau modifying query; clear toàn context có thể làm mất pending changes, xem [JPA Query Methods — Modifying Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.modifying-queries). Không đề xuất bật clearAutomatically vô điều kiện như một fix đã chứng minh.

## D. Test Execution Evidence

### Environment và lựa chọn command

- Đã inspect pom trước chạy. Working pom: Java target21, Spring Boot3.5.16, Surefire/Failsafe3.5.6, Testcontainers1.21.4; Hibernate runtime6.6.53.Final. Maven wrapper trỏ Maven3.9.16. Local Java runtime **22.0.2**, compilation release21; chưa chạy dưới runtime JDK21.
- Docker client/server29.2.0, Docker Desktop4.59.1, linux/arm64 server. Containers thực sự start: Ryuk0.12.0, `postgres:15-alpine` (aggregate log PostgreSQL15.19), `redis:7-alpine`. PostgreSQL17 được dùng trong compose/CI service nhưng không phải DB của IT vừa chạy.
- Lần `docker version`/`docker info` trong sandbox bị `permission denied` trên `/Users/thaichithanh/.docker/run/docker.sock`. Rerun được phép ngoài sandbox xác nhận Docker hoạt động. Shell batch ban đầu có command Java phía sau nên exit code chung=0; **không coi exit0 đó là Docker PASS**. Không có lần Maven IT thất bại vì Docker trong audit này.
- Tests chạy ngoài sandbox vì Docker socket và Mockito Java-agent attachment cần quyền; không sửa dependencies/config để vượt môi trường.
- Không có `application-test.yaml` hoặc fixture SQL riêng trong test resources; profile test lấy production config với overrides AI:15 và DynamicPropertySource host/port DB/Redis. DB thật mới, Flyway enabled, Hibernate `ddl-auto=validate`.
- Commands Maven bên dưới chạy tại `backend/`; stdout/stderr được redirect ra log `/tmp`, không lưu thêm file source/test/config trong repo. Maven tự tạo build artifacts trong `backend/target`.

| ID | Command đã chạy | Test count | Passed | Failures | Errors | Skipped | Exit | Kết thúc (+07:00) | Log |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| E1 | `./mvnw -Dit.test=SeatHoldIT test-compile failsafe:integration-test failsafe:verify` | 3 | 3 | 0 | 0 | 0 | 0 | 19:17:54 | `/tmp/phase1-audit-seat-hold.log` |
| E2 | `./mvnw -Dit.test=BookingConcurrencyIT test-compile failsafe:integration-test failsafe:verify` | 2 | 2 | 0 | 0 | 0 | 0 | 19:19:07 | `/tmp/phase1-audit-booking-concurrency.log` |
| E3 | `./mvnw -Dit.test=BookingInvariantsIT test-compile failsafe:integration-test failsafe:verify` | 12 | 12 | 0 | 0 | 0 | 0 | 19:20:07 | `/tmp/phase1-audit-booking-invariants.log` |
| E4 | `./mvnw verify` — Surefire | 33 | 33 | 0 | 0 | 0 | 0* | 19:22:16 | `/tmp/phase1-audit-verify.log` |
| E4 | `./mvnw verify` — Failsafe | 18 | 18 | 0 | 0 | 0 | 0* | 19:22:16 | Cùng log E4 |
| E5 | `./mvnw clean verify` — Surefire | 33 | 33 | 0 | 0 | 0 | 0* | 19:32:46 | `/tmp/phase1-audit-clean-verify.log` |
| E5 | `./mvnw clean verify` — Failsafe | 18 | 18 | 0 | 0 | 0 | 0* | 19:32:46 | Cùng log E5 |

`*` Mỗi E4/E5 là **một command**, exit0 chung, 51 distinct test cases. E1–E3 chạy lại cùng 17 Phase1 test được E4/E5 bao phủ; không cộng số lần lặp thành số coverage độc lập. Tổng 119 invocations qua 5 command, 51 distinct cases. Narrow commands đi qua test-compile rồi gọi cả failsafe integration-test và verify; không chỉ compile hoặc chạy integration-test mà bỏ qua bước quyết định build result.

Evidence trích ngắn để lưu bền trong báo cáo (log `/tmp` và reports target có thể bị xóa ở phiên sau):

```text
SeatHoldIT: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BookingConcurrencyIT: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BookingInvariantsIT nested groups: 2 + 4 + 4 + 2 = 12
Successfully applied 8 migrations to schema "public", now at version v8

Alice Error: com.thanh.backend.common.exception.AppException: Seat is not available
Alice Booking Response: NULL
Final Seat Status in DB (raw JDBC): HELD
Final Seat HeldByUser in DB (raw JDBC): 4
Bob's Redis Key Value: 4

SQLState: 23505
ERROR: duplicate key value violates unique constraint "booking_pnr_key"
Detail: Key (pnr)=(DUP999) already exists.

Aggregate Surefire: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
Aggregate Failsafe: Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

SQL error ở test PNR là **expected rejection được assertThrows bắt**, không phải test failure. BI outer class được báo `Tests run: 0` vì cases nằm trong nested groups; đã cộng 12 từ group reports và đối chiếu summary, không bỏ qua nested tests.

Reports aggregate: `backend/target/surefire-reports/`, `backend/target/failsafe-reports/`, `backend/target/failsafe-reports/failsafe-summary.xml`. Sau E4, phát hiện một artifact Surefire cũ `TEST-com.thanh.backend.booking.service.BookingConcurrencyIT.xml` ngày 2026-10-12, tests=1; cộng mù tất cả XML sẽ thành34 thay vì33. Vì vậy đã chạy E5 clean verify, loại artifacts cũ và build lại từ source. Counts chính thức dựa invocation mới, không dựa report lịch sử. Mockito/ByteBuddy có warning dynamic agent trên JDK22, nhưng tests khởi tạo được và pass. JaCoCo là reporting, không có coverage threshold gate và không chứng minh concurrency scenario coverage.

### Đánh giá độ tin cậy test infrastructure

- Không có outer test `@Transactional` trong AI/ba booking IT. Service gọi qua Spring proxy, worker/main thread có transaction riêng. `@Autowired EntityManager` là transaction-bound proxy dùng trong worker; không phải chia sẻ một raw EntityManager cố định giữa thread.
- DB/Redis thật hoạt động, migrations chạy thật. Hold20 test không dùng Mockito và assert winner persisted. Không suy ra DB locking từ mock payment tests.
- Race test dùng CountDownLatch có timeout, Future.get có timeout, finally mở latch và shutdownNow; không Thread.sleep. SeatHold20 cũng bounded waits nhưng thiếu unconditional cleanup khi lỗi (F09). Expiry race được mô phỏng bằng DB save + key delete, không giả `claimHeldSeatForBooking` result.
- `@MockitoSpyBean` reset mặc định giữa test methods; không có custom reset override trong source. Aggregate pass khi các context/spy combinations cùng chạy; chưa có bằng chứng spy leak, nhưng replacement save là limitation F06.
- AI cleanup sau mỗi test flush Redis DB và truncate tất cả table trừ flyway_schema_history; identity sequences không reset. Fixture lookup-or-create xử lý airport seed bị truncate. Sequential full verify không có test leakage được quan sát; không có bằng chứng an toàn parallel. Containers static được chia sẻ trong JVM và Ryuk quản lý lifecycle; không có `@Testcontainers` annotation nhưng static `.start()` thực sự đã chạy, không chỉ khai báo container.
- Không tìm thấy `@Disabled`, deliberate skip hoặc Thread.sleep trong booking IT. `*IT` bị Surefire exclude là có chủ đích, Failsafe discover đúng khi tới verify. CI bỏ lỡ lifecycle là một gap khác. Chưa chạy stress/repeat đủ để khẳng định không flake; chưa đo lock wait/isolation level hay verify target JDK21/PostgreSQL17.
- IT gọi trực tiếp service với User fixture, không đi HTTP/JWT filter. Authenticated user ID truyền vào conditional update đúng là `savedBooking.getUser().getId()`; controller production lấy user từ `@AuthenticationPrincipal` (`backend/src/main/java/com/thanh/backend/booking/controller/BookingController.java:56`). Chưa có full HTTP booking authorization IT.
- Không gọi payOS thật: booking IT không gọi createPaymentLink/webhook; test fixture dùng dummy credentials, deadline future. Payment unit tests mock gateway và transaction collaborators; cleanup scheduler không có expired payment fixture để gọi gateway trong IT. Không dùng dummy credentials làm lý do cho phép gọi mạng provider.

## E. Remaining Phase 1 Work

Thứ tự giữ kế hoạch audit: mục1–2 DONE F01; mục3 DONE F02; Redis atomicity DONE F03; Fixed Hold Deadline tại K; managed transaction consistency F04 tại L. Các residual TTL/crash/UNKNOWN outcome và gaps F05–F10 vẫn được ghi riêng.

1. **DONE — Regression cho expiry tại claim (F01).** Đã dựng deterministic pause trước claim, expiry DB/Redis thật, không Bob; RED cho thấy booking sai được commit, GREEN xác nhận reject và zero partial writes.
2. **DONE trong phạm vi F01 — Sửa final claim expiry.** Dùng PostgreSQL clock_timestamp tại WHERE; giữ status/owner và check update count hiện có. Redis lease/consume atomicity chưa sửa; tiếp tục nằm trong F03/F04.
3. **DONE — Kiểm chứng và sửa stale hold writer / double active seat (F02).** Hai RED thật sau F01; DB hold CAS bảo vệ transition cùng claim CAS; 5 STC cases GREEN trong clean verify57/57. Không thêm schema constraint; đánh giá trade-off ở I.
4. **DONE F03 và F04 managed outcomes; residual recovery limits tại L.** Booking chỉ compare-delete sau commit; hold known rollback enqueue guarded cleanup; fixed deadlines không gia hạn. F04 có16 IT, final clean verify95/95. Crash/outage dùng original TTL, không hứa immediate/durable reconciliation hoặc atomic cross-store commit.
5. **P1 — Tăng độ tin cậy BC/SH fixtures (F06/F09).** Pause seam/delegation bảo toàn repository behavior, executor finally + termination, Redis connection cleanup, test env độc lập và fixture IDs chắc chắn unique.
6. **P1 — Hoàn thiện assertions/missing cases ở matrix.** Leg order/direction, Passenger/Document/guardian liên kết đúng, infant no seat/price, rollback mọi bảng/add-ons, TTL bounded đúng config, normalization, guardian index variants và createBooking bằng hold user khác.
7. **P1 — Duplicate-active concurrency và PNR collision policy (F07/F08).** Tạo tests deterministic bằng DB thật và chốt phạm vi invariant/retry. Không thuộc payment concurrency Phase2.
8. **P1 — Version-control và CI gate (F05).** Sau review, stage đúng scope các thay đổi local; CI verify + reports + no silent IT skip. Có thể làm sớm song song về mặt kế hoạch, nhưng không push unreviewed dirty working tree.
9. **Sau các mục trên — Đồng bộ docs và xác minh release environment.** Cập nhật key/lifecycle (F10), chạy JDK21 và PostgreSQL target, lưu evidence clean checkout/CI. Giữ payment concurrency chuyên sâu ngoài scope audit này.

## F. Definition of Done

Chỉ tuyên bố Phase 1 hoàn thành khi:

- Cả ba nhóm có test thực thi đúng production behavior trên PostgreSQL/Redis thật, được track trong Git và discover bởi Maven/CI. Existing17 cases tiếp tục pass, nhưng không lấy con số17 làm fixed target nếu bổ sung tests.
- Expired/foreign/missing/mismatched holds bị reject, kể cả expiry giữa validation và claim; TTL/owner/status được kiểm tra ở điểm transition thích hợp.
- Không có double active booking cùng FlightSeat trong các lịch đã xác định: hold-vs-hold, booking-vs-reclaim cả hai thứ tự và competing booking requests. Không dùng mock result làm bằng chứng SQL/Redis atomicity.
- Booking creation failure trước/sau insert, giữa nhiều seat hoặc khi flush/commit không để partial Booking/Leg/Passenger/Document/Ticket/AddOn, không phá hold hợp lệ của request khác; recovery DB/Redis có contract/test.
- Happy path kiểm tra committed graph đầy đủ, status/PNR, pricing theo rule hiện tại, deadline15m, seat BOOKED/clear metadata và consume key đúng lease.
- Document uniqueness/normalization, guardian ratios/index/link, active-status rules và different-flight/CANCELLED positive paths có assertions DB/side effects tương ứng. Cạnh tranh duplicate-active passenger có quyết định và bằng chứng phù hợp invariant.
- PNR format, retry policy và PostgreSQL UNIQUE constraint được kiểm tra đúng failure mode, có flush/commit và xác nhận constraint; graceful collision handling không chỉ dựa mocked exists-loop.
- Không còn finding Critical/High chưa xử lý hoặc chưa có quyết định review rõ ràng. Không thay missing evidence bằng lời khẳng định từ session trước.
- CI chạy verify, lưu Surefire/Failsafe counts và không silently exclude/skip IT. Có clean-checkout evidence dưới Java21 và PostgreSQL target được chọn, không chỉ dirty local JDK22/PG15.
- Cleanup/executors/containers ổn định khi failure; documented parallel policy; không test nào gọi payment provider thật. Payment mocks chỉ chứng minh logic trong scope của chúng.
- Tài liệu mô tả đúng Redis convention, lazy expiry, booking/payment deadline và milestone; findings còn ngoài Phase1 được phân loại rõ, không chuyển sang implement Phase2.

## G. Proposed Next Step

**F01/F02/F03, Fixed Hold Deadline và F04 đã được người dùng chỉ định, evidence ở H/I/J/K/L với phạm vi/giới hạn riêng.** Final clean verify95/95 PASS. Các task còn lại ở E chờ review/chỉ định; không tự động bắt đầu F05–F10 hoặc Phase2.

Tiêu chí F01 đã verify: dùng PostgreSQL/Redis thật, coordination có timeout/cleanup, không mock claim hoặc thay save behavior; Alice qua validation, hold hết hạn, resume → `SEAT_NOT_AVAILABLE`, không Booking/Leg/Passenger/Document/Ticket/AddOn commit và seat không BOOKED.

## H. F01 Follow-up — RED → GREEN → REGRESSION

### Thay đổi và transaction semantics

- Production change của task này chỉ gồm một predicate và comment ở `FlightSeatRepository.java:20`: `s.heldUntil > cast(function('clock_timestamp') as OffsetDateTime)`. Không đổi signature/enum binding, status/owner predicate, transaction propagation, hoặc `BookingService`.
- Hibernate sinh SQL: `held_until > cast(clock_timestamp() as timestamp(6) with time zone)`. DB thời gian thực được đánh giá trong query UPDATE; không truyền `now` Java lấy ở đầu request. Null/expired held_until không thỏa predicate.
- PostgreSQL `CURRENT_TIMESTAMP`/`now()` là thời điểm đầu transaction, còn `clock_timestamp()` trả thời gian thực ngay lúc gọi, kể cả trong statement. [PostgreSQL 15 — Current Date/Time](https://www.postgresql.org/docs/15/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT). CAST xác định kiểu temporal cho Hibernate/Spring Data; dùng cú pháp [HQL cast/function](https://docs.hibernate.org/orm/6.6/querylanguage/html_single/) tương thích query rewriting thực tế đã chạy.
- Claim count0 vẫn ném `AppException(SEAT_NOT_AVAILABLE)` tại caller, transaction createBooking rollback toàn bộ. Redis DEL, managed-entity setters và Ticket/AddOn insert của seat này chỉ xảy ra sau claim thành công nên không chạy trong F01 rejection.

### Test deterministic và phạm vi chứng minh

Test: `BookingConcurrencyIT#createBooking_HoldExpiresAfterValidation_RejectsFinalClaimAndRollsBack` (`BC:448`).

1. Tạo một Alice, một seat và hold thật qua production service với TTL bình thường; request có adult document và meal add-on. Không có Bob.
2. Gắn MethodInterceptor vào Spring Data proxy **chỉ trong test**, dừng ngay trước `claimHeldSeatForBooking`. Interceptor kiểm tra transaction active và bốn insert Booking/Leg/Passenger/Document thật đã nhìn thấy trong transaction Alice.
3. Main thread không có outer transaction và chưa thấy những insert đó trên connection độc lập. DB vẫn HELD đúng Alice/deadline tương lai, Redis đúng Alice và TTL dương: Alice đã qua validation khi hold hợp lệ.
4. Main rút ngắn DB deadline thành `clock_timestamp()+1 second` và Redis TTL thành 1 giây, chỉ sau khi barrier đã tới. Awaitility polling có timeout chờ cả DB deadline qua và key tự hết hạn; không DEL key, không backdate vào quá khứ, không phụ thuộc tốc độ chạy để kịp validation với TTL1s.
5. Resume worker. Guard query trong chính transaction Alice xác nhận **`CURRENT_TIMESTAMP < held_until <= clock_timestamp()`**. Do đó test phân biệt được transaction-start clock với thời gian tại claim. `invocation.proceed()` gọi repository/transaction advice/SQL/affected rows thật; không stub claim hoặc tự persist/merge/save thay production.
6. Sau Future hoàn tất, assertions JDBC ngoài transaction kiểm tra error `SEAT_NOT_AVAILABLE`, response null, cả sáu bảng `booking`, `booking_legs`, `passengers`, `passenger_documents`, `tickets`, `add_on_services` đều count0; seat vẫn HELD đúng Alice và còn expired metadata, Redis key vẫn absent.
7. Finally luôn mở latch, shutdown/await worker và gỡ interceptor trước base cleanup. Test không thêm production hook, không thay test race cũ dùng BookingLegRepository spy. Lease được rút ngắn có kiểm soát để test nhanh; không chứng minh reclaim/renew/consume concurrency của F02–F04.

### Commands và execution evidence

Chạy tại `backend/`, ngoài sandbox để truy cập Docker socket/Mockito agent. Các run dùng PostgreSQL15.19 qua `postgres:15-alpine`, Redis7 qua `redis:7-alpine`, Flyway V1–V8, Java22.0.2 compile release21. Không gọi payOS. `clean` dùng để tránh artifact test-class không hợp lệ đã gặp ở lượt đầu; không thay source/config để giải quyết môi trường.

Command test F01 (R0 không có `clean`; các lượt còn lại có):

```sh
./mvnw clean '-Dit.test=BookingConcurrencyIT#createBooking_HoldExpiresAfterValidation_RejectsFinalClaimAndRollsBack' test-compile failsafe:integration-test failsafe:verify
```

Command regression:

```sh
./mvnw clean '-Dit.test=SeatHoldIT,BookingConcurrencyIT,BookingInvariantsIT' test-compile failsafe:integration-test failsafe:verify
```

| Run | Kết thúc 2026-10-26 (+07:00) | Tests | Pass | Failures | Errors | Skipped | Exit | Log / Kết luận |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| R0 — discovery | 21:26:33 | 0 | 0 | 0 | 0* | 0 | 1 | `/tmp/f01-red.log`: JUnit discovery lỗi NoClassDefFoundError: User do class artifact không hợp lệ; **không phải business RED** |
| R1 — RED clean | 21:28:12 | 1 | 0 | 1 | 0 | 0 | 1 | `/tmp/f01-red-clean.log`: chưa sửa production; booking thành công sai, seat BOOKED, sáu bảng đều count1 |
| R2 — RED xác nhận | 21:29:59 | 1 | 0 | 1 | 0 | 0 | 1 | `/tmp/f01-red-final.log`: assertion owner null báo rõ thay cho NPE phụ; production vẫn chưa sửa, cùng business failure |
| G0 — predicate thử | 21:31:01 | 1 | 0 | 1 | 0 | 0 | 1 | `/tmp/f01-green.log`: typed function bị query rewriting sai; InvalidDataAccessApiUsageException, không phải GREEN |
| G1 — diagnostic | 21:31:50 | 1 | 0 | 1 | 0 | 0 | 1 | `/tmp/f01-green-diagnostic.log`: thêm thông tin exception vào assertion, xác định `function(clock_timestampas OffsetDateTime)`; không nới expected error |
| G2 — GREEN final | 21:32:42 | 1 | 1 | 0 | 0 | 0 | 0 | `/tmp/f01-green-final.log`: CAST/FUNCTION sinh đúng SQL clock_timestamp, SEAT_NOT_AVAILABLE và toàn bộ rollback assertions PASS |
| REG — ba nhóm Phase1 | 21:34:18 | 18 | 18 | 0 | 0 | 0 | 0 | `/tmp/f01-regression.log`: SeatHoldIT3 + BookingConcurrencyIT3 + BookingInvariantsIT12 |

`*` R0 summary báo tests/errors0 vì test engine không discover được test; Maven exit1 và fork exception là lỗi infrastructure, không ghi nhận PASS. R1/R2 là bằng chứng RED trước production change; assertions semantic giữ nguyên từ RED tới GREEN, chỉ làm thông báo lỗi/owner assertion rõ hơn. G0/G1 là failed implementation attempts được sửa bằng query syntax, không tắt test hoặc chấp nhận sai exception.

Trích evidence RED đã lưu trong log:

```text
Unexpected null value, expected: <AppException> but was: <null>
Expired hold must not produce a successful booking: expected null, actual BookingResponse
booking / booking_legs / passengers / passenger_documents / tickets / add_on_services:
  expected count 0, actual count 1 (each table)
Expired seat must not become BOOKED: expected HELD, actual BOOKED
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

Evidence GREEN/regression:

```text
and fs1_0.held_until>cast(clock_timestamp() as timestamp(6) with time zone)
F01 GREEN: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
SeatHoldIT: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BookingConcurrencyIT: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BookingInvariantsIT: nested cases total 12, no failures/errors/skips
Regression: Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Đã đối chiếu XML Failsafe sau clean regression: BC3, BI12, SH3. Không chạy lại full unit/security suite hoặc BackendApplicationIT trong follow-up; các số33 unit/51 total ở baseline không phải kết quả mới sau fix F01.

### Redis review và rủi ro còn lại

Redis hiện dùng `hold:seat:{id}` → user ID, không lease generation. Validation GET owner trước claim; success path DEL không compare-and-delete. Task chỉ bổ sung DB expiry predicate; không bảo đảm key revoked/mismatched khi DB deadline vẫn còn hạn, không giải quyết GET/DEL/EXPIRE race, stale DB writer hoặc multi-seat Redis rollback. **F02/F03/F04 vẫn mở**, cùng các gap F05–F10; F02 cần được đánh giá lại với predicate mới trước khi kết luận lịch exploit cũ còn tái hiện được.

Git review: so với trạng thái bắt đầu task, `BookingService.java` không đổi (đối chiếu diff snapshot); FSR chỉ thêm comment và expiry predicate vào conditional claim vốn có. BC thêm imports, một test và helper assertion; không sửa assertions hoặc persistence stub của các test cũ. Không đổi migrations/pom/CI/dependencies. Hai tài liệu được cập nhật/tạo theo yêu cầu. Các thay đổi local/untracked có sẵn vẫn được giữ; chưa commit hoặc push.

Đó là điểm dừng của task F01. Người dùng sau đó đã chỉ định F02, được ghi riêng tại mục I; task F01 không tự chuyển sang F02.

## I. F02 Follow-up — Stale Hold Writer / Double Active Booking

Cập nhật **2026-10-26 23:34 +07:00**; working tree `main`/HEAD `e783773`, chưa commit/push. Đã inspect lại service, repository, entity/BaseEntity, migrations V1–V8, Redis code, transaction configuration và tests hiện tại. Không dựa vào lịch exploit của audit làm kết luận trước khi chạy.

### Root cause và ảnh hưởng F01

F01 giữ nguyên trong source: final claim yêu cầu DB HELD, đúng owner và `held_until > clock_timestamp()`. **Lịch cũ “Alice old lease hết hạn → Bob đọc reclaimable → Alice claim thành công bằng old lease” không hợp lệ sau F01.** Nếu Bob chưa đổi owner, predicate expiry đã false; nếu Bob đã đổi owner, owner predicate cũng false. Không sửa clock/UPDATE result để ép Alice thành công. Test F01 và race Bob-before-Alice hiện có chạy lại PASS trong full verify.

Nhưng F01 không bảo vệ một transaction hold đã giữ snapshot cũ khi **transaction khác lấy lease mới hợp lệ** rồi booking. Trước fix F02, hold đọc/validate một entity, gọi Redis rồi mutate/save entity đó. FlightSeat/BaseEntity không có version; Hibernate UPDATE lúc flush chỉ có `WHERE id=?`, không recheck status/owner hiện tại. Redis SETNX đúng tại thời điểm acquire không fence toàn bộ vòng đời snapshot DB. Redis key bị xóa sau valid claim, nên một holder đã đọc từ trước có thể acquire key sau khi booking commit và ghi đè row BOOKED.

Đây đã là **bug tái hiện**, không chỉ suy luận từ việc nhìn thấy save.

### Interleaving đã phân loại

| Interleaving | Evidence thực tế | Kết luận |
| --- | --- | --- |
| Hold vs hold, Alice live lease vẫn tồn tại | STC:152 PASS trước/sau fix; Bob snapshot AVAILABLE cũ nhưng GET thấy Alice, bị SEAT_HELD; DB/Redis Alice, không booking records | Lịch này đã bị Redis chặn; không cố ép RED |
| Hold vs booking, Bob đọc AVAILABLE trước | STC:95 variant false RED trước CAS; Alice hold/valid claim/commit thật; Bob resume ghi HELD rồi Bob booking thật | Bug xác nhận, CAS làm GREEN |
| Expiry/reacquire vs booking | STC:95 variant true: old lease DB/Redis expired tự nhiên; Bob đọc expired row; Alice reacquire **new live lease**, valid booking commit; Bob resume từ old row | Bug xác nhận sau F01; khác lịch old-lease claim đã bị F01 chặn |
| Create vs create, cùng user/live hold, documents khác nhau | STC:184 PASS trước/sau fix; hai backend PID/transaction khác nhau, UPDATE2 chờ row update của UPDATE1, sau commit WHERE recheck trả0 | Một booking thành công, một SEAT_NOT_AVAILABLE; loser rollback toàn graph |
| Hold ghi trước claim | Owner/status predicates trong claim và existing Bob-before-Alice IT được chạy lại; F02 bổ sung symmetric guard cho hold khi claim ghi trước | Không suy ra atomic Redis/DB hoặc mọi writer khác đều được bảo vệ |
| Cùng owner renew live hold | STC:167 compatibility case PASS sau fix | Giữ business rule renew hiện có; không kiểm chứng atomic renewal F03 |

### Timeline RED và SQL/lock evidence

Test file: `backend/src/test/java/com/thanh/backend/booking/service/SeatTransitionConcurrencyIT.java` (không Mockito/spy). Fixtures SQL chỉ tạo users/flight/seat; hold/booking workflows gọi service/repositories thật. Test-only MethodInterceptor gọi `invocation.proceed()` và chỉ dừng sau real `findById`, hoặc quanh real claim để quan sát lock; không thay save/persist/UPDATE result. Không outer test transaction; asserts dùng JDBC ngoài worker transaction.

Timeline hai failing cases trên **production sau F01, trước F02 fix**:

1. Bob bắt đầu transaction H, READ COMMITTED, read seat AVAILABLE (variant1) hoặc old HELD expired (variant2). Pause sau real read, trước Redis acquisition; managed entity giữ snapshot cũ.
2. Alice gọi production holdSeats, lấy fresh Redis lease và DB hold có deadline tương lai. Alice createBooking bình thường, final claim F01 thành công với lease mới, commit Booking/Leg/Passenger/Document/Ticket/AddOn. JDBC xác nhận graph1, seat BOOKED/null hold, Redis absent trong khi H vẫn pause. Main connection khác Bob backend PID.
3. Resume H. Bob GET thấy key absent, SETNX thành công; stale entity save/dirty checking flush UPDATE theo id, làm DB **BOOKED → HELD Bob**. JDBC snapshot và Redis owner Bob đều được chụp ngay sau H commit.
4. Bob gọi createBooking với document khác bằng production service, dùng chính hold vừa lấy; valid claim thật thành công. JDBC count active bookings cùng seat = **2**, cả6 bảng graph count2, hai booking PENDING/tickets persisted. Không fake một claim invalid thành success.

Trích RED `/tmp/f02-red.log`, 23:30:52:

```text
Bob read: pid=61, tx=774, isolation=read committed, seat=AVAILABLE
Alice committed valid booking while Bob's read transaction is still paused
expiredInitialHold=false: afterStaleWriter=HELD Bob, redisOwner=Bob,
  holdError=null, bobBookingError=null, activeBookings=2

Bob read: pid=61, tx=789, isolation=read committed, seat=HELD old Alice expired
expiredInitialHold=true: afterStaleWriter=HELD Bob, redisOwner=Bob,
  holdError=null, bobBookingError=null, activeBookings=2

Hibernate stale entity UPDATE: SET ... status=?, held_by_user=?, held_until=? ... WHERE id=?
Tests run: 4, Failures: 2, Errors: 0, Skipped: 0
```

Hai cases PASS trong cùng lượt RED không bị thay để fail. Booking-vs-booking ghi nhận thực tế:

```text
booking claim1: pid=61, tx=763, isolation=read committed
booking claim2: pid=62, tx=764, isolation=read committed
pg_stat_activity: pid=62, wait_event_type=Lock, wait_event=transactionid, blockers={61}
```

Test dùng `pg_blocking_pids` xác nhận đúng blocker; first claim thực thi UPDATE thật rồi pause trước commit/Redis DEL, second request đọc committed live hold/Redis owner thật và chờ UPDATE. Khi first commit, second trả0 và service rollback. Đây phù hợp [PostgreSQL READ COMMITTED — UPDATE wait/recheck](https://www.postgresql.org/docs/15/transaction-iso.html#XACT-READ-COMMITTED); lock tồn tại tới transaction end theo [Explicit Locking](https://www.postgresql.org/docs/15/explicit-locking.html). Không lấy Mockito interaction làm evidence locking.

### Minimal remediation đã chọn

1. `FlightSeatRepository.java:22` thêm `acquireHoldIfAvailable(seatId, user, expiresAt)` conditional UPDATE:
   - Chỉ row AVAILABLE, hoặc HELD đã expired theo DB wall clock, hoặc HELD cùng owner mới được chuyển/renew thành HELD.
   - **BOOKED không bao giờ thỏa WHERE.** Kiểm tra trên DB row tại UPDATE, không dựa snapshot đã đọc.
   - Ghi owner/deadline và updatedAt (bulk query không chạy entity auditing listener).
2. `BookingService.java:150` thay setters/save hold bằng CAS; affected rows0 ném SEAT_HELD, giữ try/catch rollback/Redis cleanup hiện có.
3. Không mutate managed FlightSeat sau CAS. Nếu tự set lại fields rồi save/dirty flush, có thể tái tạo unconditional UPDATE; entity hiện chỉ dùng để validate flight/early checks, response không trả entity. Không clear toàn persistence context hoặc đổi global flush mode. Trade-off: snapshot trong context giữ state cũ tới hết hold transaction, service không dùng nó để quyết định transition lần nữa.
4. Final claim F01 không đổi. Khi booking giữ row update lock, hold CAS chờ nếu cần rồi PostgreSQL đánh giá predicate trên row mới; BOOKED không được reopen. Khi hold thay owner trước, claim owner predicate từ chối booking cũ.

Đã cân nhắc nhưng không chọn:

- **Pessimistic lock từ initial read:** có thể serialize nhưng giữ lock qua Redis I/O và phần đọc/validate dài hơn, tăng contention/lock ordering trong batch. CAS đủ cho bug đã xác nhận, dựa row-lock do UPDATE tự lấy, không thêm SELECT FOR UPDATE.
- **Chỉ dựa Redis SETNX:** RED chứng minh Redis đúng tại acquire vẫn không bảo vệ snapshot DB trước đó; không đủ.
- **Version column/optimistic locking:** thêm schema và conflict handling trong khi CAS trực tiếp thể hiện state rule; chưa cần cho fix này.
- **Unique active-ticket index:** defense-in-depth có ích nhưng không tự ngăn BOOKED → HELD, cần chốt ticket/booking terminal lifecycle và xử lý dữ liệu duplicate cũ trước migration. Không thêm schema/constraint trong task này. V7 chỉ unique seat number/flight, không unique active tickets/seat.
- **Lease tokens/Lua/Redis redesign:** chưa cần để ngăn F02 DB overwrite; không triển khai hoặc tự mở scope F03/F04.

### GREEN và full regression

```sh
# RED trước production fix; chạy cùng command để GREEN sau CAS
./mvnw clean -Dit.test=SeatTransitionConcurrencyIT test-compile failsafe:integration-test failsafe:verify

# Full regression, gồm thêm compatibility case live renewal
./mvnw clean verify
```

Chạy tại `backend/`, ngoài sandbox, PostgreSQL15.19 và Redis7 Testcontainers thật, Flyway8 migrations, Java22.0.2/release21. Không gọi payOS. Không thêm dependencies hoặc sửa Maven/CI. Tất cả waits có timeout/finally, worker terminate và interceptor được gỡ trước base cleanup.

| Stage | Kết thúc 2026-10-26 (+07:00) | Tests | Pass | Failures | Errors | Skipped | Exit | Log |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| RED — production sau F01 | 23:30:52 | 4 | 2 | 2 | 0 | 0 | 1 | `/tmp/f02-red.log` |
| GREEN — same4 cases sau CAS | 23:32:59 | 4 | 4 | 0 | 0 | 0 | 0 | `/tmp/f02-green.log` |
| Full clean verify — Surefire | 23:34:51 | 33 | 33 | 0 | 0 | 0 | 0* | `/tmp/f02-clean-verify.log` |
| Full clean verify — Failsafe | 23:34:51 | 24 | 24 | 0 | 0 | 0 | 0* | Cùng log |

`*` Một command clean verify, exit0 chung, **57 distinct test cases**. Failsafe = STC5 + SeatHold3 + BookingConcurrency3 (có F01) + BookingInvariants12 + context1. STC test thứ5 bảo đảm same-owner live renewal vẫn được phép, thêm sau GREEN concurrency4 và verify trong full run; không thay assertions của4 cases RED/GREEN. Không cộng lượt chạy lặp thành coverage độc lập.

GREEN của hai stale writer cases:

```text
afterStaleWriter=BOOKED, held_by_user=null, held_until=null, redisOwner=null
holdError=AppException(SEAT_HELD)
bobBookingError=AppException(SEAT_NOT_AVAILABLE)
activeBookings=1; graph rows=1 in each of six tables; Bob Booking/Passenger rows=0
```

Full verify còn ghi nhận blocked UPDATE pid71 chờ pid70 (`transactionid`), rồi loser rollback; XML Surefire/Failsafe khớp33/24, không skipped. SQL error `booking_pnr_key` trong BI là expected constraint test, không phải failure. Chưa verify runtime JDK21/PostgreSQL17 hoặc stress mọi schedule; không suy ra mọi writer ngoài workflows được test đều an toàn.

### Phạm vi và rủi ro còn lại

F02 resolved trong các hold/create state transitions đã kiểm thử; **Phase1 chưa đóng**. F03/F04 vẫn mở: GET→EXPIRE/DEL không atomic, không lease generation, plain DEL sau claim, Redis side effects không rollback cùng DB và multi-seat/commit-time failure recovery chưa được giải quyết. CAS là DB guard, không biến DB+Redis thành một transaction. Không sửa cancellation/payment lifecycle hoặc trực tiếp triển khai defense constraint cho arbitrary writers.

Files của task F02: `BookingService.java` (hold mutation → CAS), `FlightSeatRepository.java` (hold CAS, giữ F01), thêm `SeatTransitionConcurrencyIT.java`, cập nhật audit/status. `FlightSeat`/BaseEntity, migrations, pom, CI và các test cũ không đổi bởi task này. Đã đối chiếu production diff với snapshot khi bắt đầu, giữ nguyên thay đổi local có sẵn. Chưa stage/commit/push; dừng tại F02, không tự bắt đầu F03/F04 hoặc Phase2.

## J. F03 Follow-up — Atomic Redis Lease Operations

Ngày **2026-10-27**, evidence mới nhất **01:02:50 +07:00**; working tree `main`, HEAD `e783773`, chưa commit/push. Đã đọc lại AGENTS/status/audit và đối chiếu source, không mặc định kết quả F02 còn đúng. Phương án server-only UUID, tác động API/DB/Redis/tests và bỏ DEL ở cancel đã trình bày trước implementation; không cần migration/API contract change đáng kể.

### Inventory và root cause

| Site trước F03 | Behavior cũ | Sau F03 |
| --- | --- | --- |
| Initial acquisition, `holdSeats` | GET rồi SET NX+TTL userId | GET snapshot; SET NX+TTL `userId:UUID`, chỉ thành công mới trả lease. SET NX là điều kiện atomic, không ghi đè key thắng race |
| Same-user renew | GET owner → EXPIRE900, bỏ qua boolean return | Capture full identity; Lua GET==expected → PEXPIRE; return0 thì SEAT_HELD, không reread/reacquire lease mới |
| DB-CAS failure cleanup | GET owner → DEL | Map request chứa exact lease từ acquisition, Lua compare-delete |
| Batch exception cleanup | GET owner từng key → DEL | Cùng map và Lua; bao gồm lease đã acquire Redis dù DB CAS chưa thành công |
| Booking validation | GET userId | readOwned xác nhận actor, trả immutable snapshot; Map theo seat truyền nội bộ tới final consume |
| Consume sau DB claim | DEL vô điều kiện | Lua compare-delete lease từ validation, không phải GET lease hiện tại; false → SEAT_NOT_AVAILABLE/DB rollback |
| Cancel booking | DEL vô điều kiện theo seat ID | Không DEL: booking đã consume lease khi tạo; không có binding cho phép cancel xóa key mới |
| Payment cleanup scheduler | Chỉ update DB, không thao tác hold key | Không đổi |

Đã rg toàn source: key builder `hold:seat:` hiện chỉ ở SeatHoldLeaseStore. JwtService còn delete Redis keys cho auth, không phải seat holds và không thuộc task. Không có release-only hold endpoint riêng.

Root cause cũ: owner check và mutation là hai Redis commands; TTL expiry/reacquire có thể chen vào. Value chỉ userId còn gây **same-user ABA**: lease1 và lease2 giống nhau, không thể nhận diện old request. DB CAS F02 không ngăn side effect Redis xảy ra **trước** CAS; RED stale-renew Bob có DB CAS reject Alice nhưng TTL Bob đã bị extend.

### Identity, authentication và propagation

- `SeatHoldLeaseStore.java:31` định nghĩa Lease(seatId, userId, UUID). UUID.randomUUID sinh mỗi **new successful SET NX acquisition**; loser bỏ candidate. Renew không SET/token rotation, giữ nguyên identity. Hết hạn rồi cùng user reacquire vẫn UUID mới.
- Value chỉ lưu Redis dưới dạng `userId:UUID`; key vẫn theo seat. Token không có trong HoldSeatsRequest/HoldSeatsResponse/CreateBookingRequest, không nhận từ client và không persist DB. User ID từ `@AuthenticationPrincipal` đi qua service; primitive renew/release còn kiểm expected.userId==authenticated actor, Lua so sánh cả owner+UUID.
- Hold flow giữ acquiredLeases per request; cleanup dùng snapshot đó thay vì đọc key mới. Booking validation trả validatedLeases map, truyền qua private helper tới consume. Không thay transaction boundaries, propagation hoặc F01/F02 query.
- Consume mismatch phải reject: chỉ DB owner cùng user chưa đủ để authorize old request dùng lease generation mới. Claim đã thực thi nhưng chưa commit, exception rollback DB, không xóa key mới. Test single-seat chứng minh behavior này.
- Không cần DB column/API token cho **server request đã snapshot lease1**. Request mới chưa đọc lease tại server sẽ dùng lease hiện tại của authenticated user; **không có client-side fencing** cho intent của HTTP request trì hoãn từ phía client. Nếu cần semantics đó phải review API contract riêng.
- Lease.toString redact token; explicit test logging cũ được chỉnh không in raw UUID. Không thay hold TTL900 hoặc payment deadline15m (`application.yaml:65`, `BookingService.java:287`).

### Atomic operations và cancel rationale

`SeatHoldLeaseStore.java:16` dùng Lua compare-and-delete; `:22` compare-and-PEXPIRE. Cả hai trả1/0, không SET khi key thiếu, không mutate value/TTL nếu mismatch. GET read-only vẫn dùng để lấy snapshot; mutation kiểm tra lại full identity trong script. Redis bảo đảm [atomic Lua execution](https://redis.io/docs/latest/develop/programmability/eval-intro/), với release pattern dựa expected unique value theo [Redis lock guidance](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/). Không dùng conditional commands mới chỉ có ở Redis version khác.

Cancel không thể chỉ GET key hiện tại rồi compare-delete: key đó có thể là lease2 của cùng user hoặc user khác, không thuộc booking cũ. Happy booking đã consume lease1, nên không có legitimate original lease cần release ở cancel. Bỏ Redis DEL giữ nguyên API/cancel DB behavior; orphan lease hoặc legacy key còn lại sẽ expire hoặc cần F04 recovery. Không tự persist token vào booking để redesign recovery.

Compatibility: legacy userId-only/malformed values fail closed, không tự upgrade/delete/renew. Chúng tự expire theo TTL. Rollout phải drain old writers, tránh chạy lẫn binary cũ có plain DEL/EXPIRE với format mới; có thể đợi cửa sổ hold tối đa900s sau khi old writers dừng. Đây là thay đổi internal Redis wire format, không phải schema migration. Không triển khai/deploy hoặc flush key thật trong task.

### Deterministic RED — production cũ giữ nguyên

`RedisLeaseConcurrencyIT` dùng PostgreSQL/Redis7 Testcontainers thật. Test-only proxies delegate command và kết quả thật, chỉ dùng latch pause sau GET hoặc trước real Lua execute. Không mock Redis results, không thay transaction/persistence/UPDATE result. Script hook cho GREEN đặt pause **trước atomic command**, không chèn vào giữa Lua execution.

**A/C — cleanup late, Bob hoặc same-user reacquire (2 cases, RLC:94):**

1. Alice gọi hold batch seat1 + missing seat2. Seat1 DB CAS đã chạy thật nhưng chưa commit; missing seat2 gây exception thật.
2. Pause cleanup Alice sau GET lease1. Cho Redis TTL1 hết hạn tự nhiên.
3. New request Bob/Alice acquire Redis lease2 bằng SET NX thật, rồi chờ row lock của Alice trước khi DB CAS hoàn tất. Test đo hai PG backend PID khác nhau và `pg_blocking_pids` xác nhận lock wait. New lease Redis tồn tại trước DB confirmation là lịch production thực tế.
4. Resume cleanup; Alice throw/rollback, new hold CAS tiếp tục và commit. Cũ DEL xóa lease2 → DB HELD đúng holder mới nhưng Redis mất. Cùng user còn có oldValue==newValue, chứng minh thiếu generation identity.
5. GREEN: Lua expected lease1 mismatch lease2, release false không xóa; rollback old DB transaction giải phóng lock, holder mới commit với đúng raw lease2/TTL. Không Booking/Leg/Passenger/Document/Ticket/AddOn partial records.

**B/C — renew late (2 cases, RLC:156):** Alice snapshot lease1 rồi pause; DB deadline/Redis TTL được rút ngắn sau barrier và chờ expire thật. Bob/Alice reacquire lease2 qua production hold. Test đặt Redis2 TTL30s như probe, không đổi production TTL. Old renew EXPIRE900 làm TTL2 tăng lên gần900s; same-user còn báo success và ghi lùi held_until DB. GREEN compare-expire mismatch → SEAT_HELD trước DB write; raw lease2, TTL không tăng, DB snapshot không đổi.

**E — key mất sau GET (1 case, RLC:198):** Redis key expire sau Alice GET trong renew. Cũ bỏ qua EXPIRE=false, vẫn update DB/báo success; GREEN reject SEAT_HELD, không tạo lại key hoặc đổi DB hold. Đây là kiểm chứng Redis renewal failure, không khẳng định Redis/DB expiry được transactionally đồng bộ.

RED log `/tmp/f03-red.log` trước mọi production edit:

```text
renew sameUser=false: TTL before=29998ms, after=899993ms
renew sameUser=true:  TTL before=29998ms, after=899987ms
cleanup sameUser=false: lease preserved=false
cleanup sameUser=true:  lease preserved=false
same-user old/new values equal; stale renewal overwrites new DB deadline
missing-key renewal returns successful HoldSeatsResponse
Tests run: 5, Failures: 5, Errors: 0, Skipped: 0
```

Không có timeout/discovery error được tính làm business RED. Cả5 failures là invariant violations, không ép fail bằng fake Redis response.

### GREEN coverage và integration boundaries

Ngoài5 cases trên, bổ sung6 cases sau khi primitive mới tồn tại, không giả chúng đã RED trước khi có API nội bộ đó:

| Requirement | Test / Location | Evidence |
| --- | --- | --- |
| UUID mới, renew giữ token, reacquire đổi token | RLC:222 | Raw format/UUID và equality checked; stale lease1 không DEL/renew lease2 |
| Wrong actor hoặc token | RLC:242 | Actor khác cả khi biết lease object, hoặc đúng owner sai UUID: false, value/TTL nguyên; Redis alone không đổi DB AVAILABLE |
| Missing/expired lease | RLC:259 | Release đúng1 lần, subsequent release/renew false; missing PTTL=-2, không resurrection, không mutate newer key |
| Legacy/malformed | RLC:277 | Không accept/renew/upgrade/delete legacy; value/TTL không tăng |
| Consume snapshot cũ vs same-user lease2 | RLC:290 | Old booking đã claim DB nhưng pause trước Lua; lease2 SET NX thật, hold2 chờ DB lock; mismatch reject/rollback old booking, hold2 commit, key2/DB HELD đúng Alice, zero graph rows |
| Cancel không xóa holder mới | RLC:334 | Bob giữ initial AVAILABLE snapshot; Alice hold/book commit; Bob SET NX2 pause trước CAS; Alice cancel commit; key2 không bị DEL; Bob CAS AVAILABLE thành HELD, old booking/ticket CANCELLED |

Primitive tests xác minh Lua/actor checks trên Redis thật, không tự biến Redis lease thành valid seat hold. Các case service integration còn assert PostgreSQL bằng fresh JDBC, DB state và graph counts. Đồng bộ có timeout/finally, worker terminate, advisors được gỡ trước cleanup. API/controller không nhận token/client userId mới.

Existing SH/BC/STC assertions được cập nhật format `userId:UUID` qua test helper `SeatHoldLeaseAssertions`, vẫn check expected authenticated owner và canonical UUID. Batch rollback và Bob-race còn so sánh **exact original raw identity** trước/sau, mạnh hơn chỉ check owner; không nới error/state/rollback assertions cũ. Test fixture expiry/DEL dùng để dựng race vẫn là thao tác fixture, không production mutation.

### Commands và kết quả

Chạy tại `backend/`, ngoài sandbox để Docker/Mockito agent hoạt động. PostgreSQL15.19, `redis:7-alpine` thực sự start, Flyway8 migrations, Java22.0.2 compile release21. Không thêm dependencies/config hoặc gọi payOS thật.

```sh
# RED, GREEN core và GREEN expanded dùng cùng command
./mvnw clean -Dit.test=RedisLeaseConcurrencyIT test-compile failsafe:integration-test failsafe:verify

# Regression toàn backend
./mvnw clean verify
```

| Stage | Kết thúc 2026-10-27 (+07:00) | Tests | PASS | FAIL | Errors | Skipped | Exit | Log |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| RED — production sau F02 | 00:53:22 | 5 | 0 | 5 | 0 | 0 | 1 | `/tmp/f03-red.log` |
| GREEN — same5 races | 00:56:42 | 5 | 5 | 0 | 0 | 0 | 0 | `/tmp/f03-green-core.log` |
| GREEN expanded | 00:59:49 | 11 | 11 | 0 | 0 | 0 | 0 | `/tmp/f03-green-all.log` |
| Full clean verify — Surefire | 01:02:50 | 33 | 33 | 0 | 0 | 0 | 0* | `/tmp/f03-clean-verify.log` |
| Full clean verify — Failsafe | 01:02:50 | 35 | 35 | 0 | 0 | 0 | 0* | Cùng log |

`*` Một command exit0, **68 distinct test cases**. Failsafe35 = F03 11 + SeatHold3 + BookingConcurrency3 + SeatTransition5 + BookingInvariants12 + context1. Surefire33 giữ Actuator24/payment8/scheduler1. XML reports sau clean khớp33/35. F01 expiry and F02 stale-writer/lock tests đều PASS; không cộng các lượt chạy lặp thành số coverage độc lập. Expected PNR constraint error không phải test failure.

GREEN core TTL không tăng: Bob ~29997→29940ms, same-user ~29998→29993ms; cleanup cả2 cases `lease preserved=true`. Đã kiểm cả stored value và TTL, không chỉ Mockito calls/HTTP status.

### Files, compatibility và scope còn lại

- Production: thêm `SeatHoldLeaseStore.java` (UUID/Lua/actor validation); sửa `BookingService.java` (request maps, consume check và bỏ cancel DEL). `FlightSeatRepository` F01/F02 byte-for-byte không đổi so snapshot đầu F03; không đổi schema/API/TTL/deadline/transaction boundaries.
- Tests: thêm `RedisLeaseConcurrencyIT.java` và `SeatHoldLeaseAssertions.java`; cập nhật value assertions/log redaction trong `SeatHoldIT`, `BookingConcurrencyIT`, `SeatTransitionConcurrencyIT`. Không sửa BookingInvariantsIT/base Testcontainers/CI/pom.
- Docs: cập nhật audit/status. Không stage/commit/push; giữ local changes có sẵn.

**F04 còn mở:** Lua không commit chung PostgreSQL; crash/network/commit failure sau Redis mutation, multi-seat consume rồi seat sau fail, rollback khôi phục lease/TTL đã renew và orphan reconciliation chưa giải quyết. Request maps chỉ bảo đảm cleanup không xóa **generation khác**, không bảo đảm khôi phục mọi lease của transaction bị rollback. Không afterCommit, outbox, distributed transaction hoặc compensation redesign.

Chưa chứng minh Redis Cluster/failover/network partition, client-side request fencing, cancellation DB concurrency hoặc mọi multi-seat/commit-time failure. Concurrent operations trong **cùng một lease token** vẫn được coi là cùng lease (renew không rotate), không phải unique identity cho từng HTTP request. Legacy rolling-version safety yêu cầu old writers drain như trên, không thể được Lua mới bảo vệ khỏi DEL vô điều kiện của binary cũ.

F03 hoàn tất trong phạm vi server-side atomic lease operations đã nêu; **Phase1 chưa đóng**. Dừng tại đây, không tự triển khai F04 hoặc Phase2.

### Follow-up phân lớp Redis adapter — 2026-10-27 08:25 +07:00

Theo yêu cầu riêng sau F03, chuyển class sang `backend/src/main/java/com/thanh/backend/booking/infrastructure/redis/SeatHoldLeaseStore.java` (package `com.thanh.backend.booking.infrastructure.redis`). BookingService giữ orchestration; adapter thể hiện rõ trách nhiệm Redis. Chỉ đổi package/file và imports tại BookingService/RedisLeaseConcurrencyIT, không thêm interface/abstraction. Body class so với snapshot trước move giống hệt, giữ nguyên Lua/UUID/TTL và toàn bộ business behavior.

```sh
./mvnw clean '-Dit.test=RedisLeaseConcurrencyIT,BackendApplicationIT' test-compile failsafe:integration-test failsafe:verify
```

Kết thúc **2026-10-27 08:25:33 +07:00**, exit0; **12 tests PASS**, failures0/errors0/skipped0 (11 F03 IT +1 context smoke test). PostgreSQL/Redis thật; context startup xác minh component scan/injection sau relocation. Log `/tmp/lease-layering-regression.log`. Không chạy lại full68 cases cho package-only move; số68 ở J là verification F03 trước move. Không thay test assertions, API/schema/transaction hoặc xử lý F04; chưa commit/push.

## K. Fixed Seat-Hold Deadline — RED → GREEN

Ngày **2026-10-27**; nhánh `main`, HEAD `e783773`, working tree có thay đổi local từ trước; chưa commit/push. Business rule do người dùng chốt trong task này thay thế renewal semantics lịch sử ở I/J: **15 phút cố định từ acquisition đầu tiên, retry cùng user không kéo dài Redis TTL hoặc PostgreSQL heldUntil; payment vẫn15 phút từ createBooking**.

### Review implementation và lựa chọn tối thiểu

Trước sửa, `BookingService.holdSeats` tính `expiresAt = requestStart +900` rồi luôn gọi hold CAS với deadline đó, kể cả live same-owner hold. `SeatHoldLeaseStore.acquireOrRenew` GET snapshot → Lua compare full identity → PEXPIRE900. F03 đã ngăn old generation mutate new generation, nhưng cùng generation vẫn được gia hạn. Response luôn900, batch exception release cả lease đã tồn tại trước request. Thời điểm Java tính deadline và Redis EXPIRE còn khác nhau nên deadline DB/Redis không chính xác bằng nhau.

Đã đọc service/repositories/tests/config/migrations và kiểm tra checkout thực tế: `frontend/app/page.tsx` passengers → seats → payment; `SeatSelection.tsx` POST hold; `VietQRPayment.tsx` auto-create booking. Không cần idempotency-key để bảo đảm fixed deadline trong một live hold. Không sửa frontend, payment flow, autosave/draft/recovery/hold session hay F04.

Thiết kế sau sửa:

1. `SeatHoldLeaseStore` giữ value **userId:UUID**, server-generated identity F03 không đổi. `acquireOrReuse` trả `Hold(Lease, expiresAt, acquired)`. New acquisition dùng Lua SET NX PX900000 và PEXPIRETIME ngay trong cùng atomic execution; deadline chính xác của Redis được persist DB. Nếu snapshot DB cùng owner còn live nhưng Redis mất, SET NX PXAT dùng deadline DB cũ, không bắt đầu15m mới.
2. Live retry capture identity như F03, rồi Lua GET==expected → PEXPIRETIME; **không mutate TTL/value**. Missing/mismatch/no-expiry trả empty; không reread/adopt/reacquire generation mới sau failed compare. Redis >=7 theo [PEXPIRETIME](https://redis.io/docs/latest/commands/pexpiretime/); [SET NX/PX/PXAT](https://redis.io/docs/latest/commands/set/) bảo đảm acquisition gắn expiry atomically. Compose/Testcontainers hiện là Redis7. `readOwned` cho booking và compare-delete không đổi.
3. Hold CAS vẫn loại trừ BOOKED, chỉ nhận AVAILABLE hoặc HELD expired/live cùng owner. Bổ sung candidate `expiresAt > clock_timestamp()` và live same-owner phải **heldUntil = candidate expiresAt**. Vì vậy snapshot cũ không được overwrite deadline mới và DB lock wait không được hồi sinh expired hold. Không mutate/save stale managed entity. Query final claim F01 **byte-for-byte giống snapshot đầu task**; createBooking/cancel/payment implementation và transaction boundaries cũng không đổi.
4. Sau tất cả CAS, compare lại exact token + deadline của từng hold. DB không lưu UUID nên CAS alone không thể fence new UUID với cùng owner/deadline. Test ABA cho real CAS affectedRows1 rồi final compare reject/rollback, giữ lease2 và DB snapshot đã commit của request mới.
5. Cleanup chỉ compare-delete lease mới do request acquire, kể cả DB CAS thất bại. Retry batch thất bại không xóa lease có trước. Đây là thay đổi ownership của cleanup trong body, không thêm afterCommit/compensation/reconciliation F04.
6. DTO/API giữ fields hiện có. Response `expiresAt` lấy **min deadline của các seat**, `ttlSeconds` là remaining time ceil seconds và cap config900. Seat mới trong mixed batch vẫn có deadline riêng, không bị renew hoặc co lại theo seat cũ. Controller text không hứa thêm15m mỗi retry.

### Deterministic tests trước production change

Thêm8 `fixedDeadline_*` cases vào `RedisLeaseConcurrencyIT` trước mọi production edit. Dùng PostgreSQL15.19/Redis7 Testcontainers thật; fixtures rút ngắn deadline bằng DB UPDATE và Redis absolute expireAt tới cùng millisecond. Chờ real expiry bằng bounded polling; không Thread.sleep/mock clock/giả affected-row hoặc Redis result. Interceptors chỉ pause/delegate real calls. DB assertions qua fresh JDBC, không dựa persistence context cũ.

| RED case | Bằng chứng trước sửa | GREEN invariant |
| --- | --- | --- |
| initialAcquireUsesExactlyOneDeadline | DB/response nanosecond deadline khác Redis expiry và precision | Một exact epoch-millisecond deadline; acquire window15m |
| retryPreservesDeadlineIdentityAndRemainingTtl | Deadline fixture60s thành900s; TTL tăng | Exact DB/Redis/response deadline giữ nguyên, UUID giữ nguyên, TTL không tăng |
| failedRetryBatchPreservesExistingLease | Missing seat2 → cleanup xóa lease1 đang live | SEAT_NOT_AVAILABLE, lease1/deadline giữ nguyên, zero booking graph |
| mixedBatchReturnsEarliestDeadlineWithoutExtendingOldSeat | Seat cũ60s bị renew900 và response sai | Min deadline60s, mỗi seat DB/Redis khớp, không extend seat cũ |
| missingRedisDoesNotExtendPersistedLiveHold | Mất key → reset DB live deadline thành900s | UUID mới dùng đúng deadline DB còn lại, không15m mới |
| dbLockOutlivesLeaseRejectsRetry | Retry renew Redis rồi chờ row lock; sau expiry vẫn success và DB deadline mới | Hai PID riêng, pg_blocking_pids xác nhận actual lock wait; expired candidate reject, DB deadline cũ, key absent |
| concurrentSameLeaseRetriesPreserveDeadline | Hai transactions cùng renew deadline | Hai PID/barrier trước SQL; cả2 success cùng exact deadline/token, không extend |
| retryPausedBeforeCasCannotOverwriteNewSameUserGeneration | Old request overwrite heldUntil lease2 và báo success | Old SEAT_HELD/rollback, exact DB snapshot/Redis lease2/deadline giữ nguyên |

Trích RED `/tmp/fixed-hold-red.log` (13:55:38 +07): fixture deadline `06:56:37.551Z` bị đổi thành `07:10:37.555550Z`; old retry có `error=null` rồi ghi lùi new DB deadline từ `14:10:37.395151` về `14:10:36.311644`. Summary **8 tests,8 failures,0 errors,0 skipped**, exit1. Các failures là business/deadline assertions; không có timeout/discovery/infrastructure error tính làm RED. Cùng8 test bodies/assertions GREEN sau sửa.

Ba tests bổ sung **sau GREEN core**, không giả là đã RED:

- `retryAfterExpiryStartsNewLease_PaymentGetsSeparateFifteenMinutes`: DB/Redis expired thật → UUID/deadline900 mới; live retry deadline60s không đổi; createBooking thật persist paymentDeadline trong before/after booking+900, BOOKED/null hold, Redis consumed, Booking/Ticket count1. Không gọi create payment link/payOS.
- `inconsistentLiveDeadlinesFailClosedWithoutMutation`: live Redis deadline30s khác DB → SEAT_HELD, DB/raw identity/Redis exact expiry không đổi; không tự reconcile.
- `oldRetryCannotAdoptNewTokenEvenWithIdenticalDeadline`: old request pause trước CAS sau compare; Redis lease1 expired còn DB live; new same-user acquisition token2 giữ đúng DB deadline; resume old CAS thật trả1 nhưng final UUID check reject/rollback. Không xóa token2.

Existing11 F03 cases giữ race schedules/state/error/rollback assertions; primitive `renew` calls chuyển sang `readDeadline` vì operation gia hạn đã bỏ. Test Redis timing proxy phát event sau Lua SET NX thật thay cho Spring setIfAbsent call cũ; không fake result. STC compatibility case đổi tên reuse và **siết deadline equality**, vẫn booking thành công. Không sửa F01/SeatHold/BookingConcurrency/BookingInvariants assertions.

### Commands và kết quả

Chạy tại `backend/`, Java22.0.2 compile release21, PostgreSQL15.19, Redis7, Flyway8 migrations. Docker socket bị sandbox từ chối nên Maven chạy ngoài sandbox theo approval; không đổi test configuration/dependencies để né môi trường. Không gọi payOS thật.

```sh
# RED trước production fix; GREEN cùng8 tests
./mvnw clean '-Dit.test=RedisLeaseConcurrencyIT#fixedDeadline*' test-compile failsafe:integration-test failsafe:verify

# GREEN expanded22 RLC +5 STC
./mvnw clean '-Dit.test=RedisLeaseConcurrencyIT,SeatTransitionConcurrencyIT' test-compile failsafe:integration-test failsafe:verify

./mvnw clean verify
```

| Stage | Tests | PASS | Failures | Errors | Skipped | Exit | Kết thúc 2026-10-27 +07:00 / log |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| RED trước sửa | 8 | 0 | 8 | 0 | 0 | 1 | 13:55:38; `/tmp/fixed-hold-red.log` |
| GREEN cùng8 tests | 8 | 8 | 0 | 0 | 0 | 0 | 13:57:41; `/tmp/fixed-hold-green-core.log` |
| GREEN expanded + F02/F03 | 27 | 27 | 0 | 0 | 0 | 0 | 13:59:29; `/tmp/fixed-hold-green-regression.log` |
| Full clean verify | **79** | **79** | 0 | 0 | 0 | 0 | **14:01:35**; `/tmp/fixed-hold-clean-verify.log` |

XML sau clean xác nhận Surefire33 + Failsafe46 = **79 distinct cases**; Failsafe46 = RLC22 + SeatHold3 + BookingConcurrency3 + STC5 + BookingInvariants12 + context1. F01 expiry rejection, F02 stale-writer/actual-lock races và F03 generation fencing đều PASS. Expected PNR unique-constraint rejection trong BookingInvariantsIT không phải test failure. Không cộng repeated invocations thành distinct coverage. Log `/tmp` và `target/*-reports` có thể bị xóa; counts/evidence được giữ trong tài liệu này.

### Giới hạn retry, rollout và phạm vi còn mở

- **Sau expiry**, request đến server có thể trở thành acquisition mới900s/UUID mới nếu DB seat và Redis cho phép; cùng payload/user không chứng minh cùng intent cũ. Có thể bị người khác giữ/BOOKED từ chối. Không thêm idempotency-key/client lease token vì không cần cho fixed live-hold deadline; không hứa idempotent retry xuyên expiry. Nếu request đã capture lease1 trước expiry thì mismatch/missing phải reject, không tự adopt lease2.
- Hai first acquisitions đồng thời cùng thấy Redis missing vẫn có thể có một SET NX loser nhận SEAT_HELD; lần retry sau sẽ đọc lease hiện tại. Token là identity một lease, không identity từng HTTP operation; client-side fencing chưa có.
- Redis thiếu trong lúc DB hold cùng owner còn live chỉ được dựng token mới với deadline còn lại. DB/Redis live deadline lệch nhau fail closed; key canonical nhưng không TTL bị reject; malformed/userId-only vẫn không upgrade/delete. Không phải recovery/reconciliation tổng quát.
- Redis7+ cần PEXPIRETIME và quyền Lua/commands tương ứng. Giá trị key không đổi nhưng **không rolling mix old writers** còn PEXPIRE900/plain DEL. Drain old writers; live holds cũ có thể có precision/deadline lệch DB/Redis nên bị reject tới expiry. Không migration, deploy, flush key hoặc production-data changes.
- **F04 vẫn OPEN:** Redis không commit/rollback chung DB; crash/network/commit-time failure, multi-seat consume rồi failure, orphan leases và compensation chưa xử lý. Final compare là check trước return, không atomic với DB commit; cleanup cùng token/overlapping transactions không có fencing theo mỗi HTTP request. Không thêm outbox/afterCompletion/distributed transaction.
- Chưa verify Redis Cluster/failover/partitions/clock skew, runtime JDK21/PostgreSQL17, mọi batch lock ordering, cancellation/payment concurrency hoặc frontend recovery. Deadline đồng nhất epoch không thay thế yêu cầu đồng bộ clock DB/Redis/app.

Files task: production `SeatHoldLeaseStore`, `BookingService.holdSeats`, hold CAS `FlightSeatRepository`, comments `HoldSeatsResponse` và hold endpoint text `BookingController`; tests `RedisLeaseConcurrencyIT` (+11) và `SeatTransitionConcurrencyIT` (reuse/equality); docs status/audit. Giữ nguyên pre-existing local changes, không thêm schema/dependencies/API fields hoặc idempotency key. Không commit/push; Phase1 chưa đóng và không tự xử lý F04/Phase2.

## L. F04 — Redis/PostgreSQL Transaction Consistency

Ngày **2026-10-27**, working tree `main`/HEAD `e783773`, local changes chưa commit/push. Đã đọc lại AGENTS/status/audit/source/tests/config, không lấy79/79 từ phiên trước làm bằng chứng mới. Chỉ F04; không frontend recovery/hold-session/idempotency-key API, distributed transaction/Kafka/outbox/schema hoặc payment concurrency redesign.

### Transaction/Redis inventory đã kiểm tra

| Flow | DB boundary và Redis trước F04 | Sau F04 |
| --- | --- | --- |
| `BookingService.holdSeats` | Spring REQUIRED; đọc entities, Redis acquire/reuse, DB CAS, compare deadline; catch trong method release acquired keys trước DB rollback; commit ngoài body | Giữ transaction/CAS/deadline. Pre-register candidate trước Redis để xử lý unknown SET result; known rollback enqueue guarded cleanup, không release trong body |
| `validateSeatSelections` | DB owner/status/deadline + Redis GET owner/UUID snapshot | Giữ authenticated ownership; snapshot `Hold` mang exact UUID/deadline DB |
| `saveTicketsAndAddOns` / createBooking | Một REQUIRED transaction cho6 bảng + seat CAS; DEL lease từng seat ngay sau claim, trước ticket/add-on/seat sau và trước commit | Chỉ compare-read sau claim; all snapshots được check lại beforeCommit. Sau actual commit mới compare-delete từng lease; error/mismatch không đổi committed result |
| Cancel Booking | REQUIRED, owner/admin check, booking/tickets CANCELLED, seat AVAILABLE; không Redis DEL từ F03 | Không đổi logic/transaction; không có snapshot lease để authorize xóa key hiện tại |
| Payment service | TransactionTemplate tách prepare/attach/cancel/webhook; locks Booking→Payment, ticket/seat mutations trong DB; provider network ngoài DB blocks; không seat-hold Redis | Không đổi |
| BookingPaymentCleanupScheduler | TransactionTemplate từng booking, Booking→Payment locks, CANCELLED/AVAILABLE DB; async provider cancel sau commit; không seat-hold Redis | Không đổi |
| SeatHoldLeaseStore | GET, Lua SET NX PX/PXAT + PEXPIRETIME, compare-read deadline, compare-DEL; không tham gia JPA rollback | Lua/key/value không đổi. Overload nhận server candidate để register cleanup trước write; không nhận client token |

Không có release-only hold endpoint, không có global seat-hold Redis writer khác trong source. Authentication Redis của JwtService không thuộc seat leases. Đã đối chiếu snapshot: **hold CAS F02/fixed deadline, final claim F01 và tất cả Lua scripts byte-for-byte không đổi**; repository chỉ thêm cleanup read/lock query. Không đổi payment/cancel business behavior.

### Phân biệt safety guards cũ với consistency gaps

- F01 wall-clock expiry tại claim từ chối DB hold hết hạn, kể cả transaction đã bắt đầu trước deadline. Không rollback Redis đã DEL.
- F02 hold/claim CAS chặn stale BOOKED→HELD và competing booking commit trong tested schedules. Không bảo vệ mọi cancellation/payment writer hoặc transaction side effects bên Redis.
- F03 full UUID + atomic comparison chặn cleanup/consume old generation xóa/renew generation mới. Không đủ khi **cùng lease** bị transaction1 xóa rồi DB rollback, hoặc cùng lease đã được transaction2 commit/adopt.
- Fixed Deadline giữ một absolute deadline/lease, không renew khi retry. Không làm method-body catch biết DB commit outcome hoặc khôi phục consumed keys.
- F04 cần đổi timing/ownership của side effects: giữ lease nguyên vẹn cho đến confirmed booking commit, và cleanup candidate theo **committed DB state sau rollback**. Không cần resurrect key/TTL, nên không có race SETNX compensation ghi đè new generation hoặc gia hạn deadline.

### Giải pháp và trade-offs đã chọn

Đã trình bày phương án trước implementation. `SeatHoldTransactionLifecycle` là component nội bộ modular monolith, sử dụng Spring transaction synchronization ([Spring6.2 API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html)).

1. **Booking:** lưu immutable Hold snapshots từ validation; compare-read sau mỗi real claim và trước commit, false/Redis error → exception/DB rollback, không DEL. `afterCommit` compare-delete exact UUID từng seat, catch RuntimeException riêng mỗi key để key sau vẫn được thử. Không throw cleanup failure sau actual commit. Independent JDBC connection quan sát Booking count1 tại thời điểm Lua DEL bắt đầu; Top-level Spring service proxy chưa trả success trong khi beforeCommit còn pause (controller hiện không có outer transaction). REQUIRED service join ambient transaction thì outer caller phải commit trước HTTP response; không đổi propagation để tự commit sớm.
2. **Hold rollback:** generate candidate UUID và register callback trước acquire/reuse call. Bao gồm trường hợp Redis SET thật thành công nhưng response mất. COMMITTED không cleanup; UNKNOWN giữ nguyên TTL và log, không đoán rollback. Known ROLLED_BACK nộp task vào queue nội bộ hữu hạn.
3. **Guarded cleanup:** precheck exact candidate hiện còn trong Redis; mở fresh `REQUIRES_NEW`, SELECT live-hold predicate `FOR UPDATE NOWAIT`. Bất kỳ committed live DB hold nào đều được giữ, kể cả transaction retry dùng lại cùng UUID đã thắng/commit. Nếu DB không có live hold, Lua compare-delete exact candidate. DB row lock được giữ xuyên check + Redis DEL để một retry không commit/adopt vào giữa. NOWAIT tránh chờ row-lock của pending retry; mismatch/lock/Redis error → original TTL fallback, không mask request exception.
4. **Connection starvation phát hiện trong review:** phương án synchronous afterCompletion + REQUIRES_NEW pass94 default-pool tests nhưng pool2 cho1 Timeout error: callback giữ completed transaction connection rồi xin connection thứ3 trong khi retry giữ connection2. Không tăng pool/timeouts hoặc nới wait để lấy PASS. Đổi riêng rollback cleanup sang `ThreadPoolTaskExecutor`1 worker/queue128, AbortPolicy; callback không đợi task, trả connection để task tiếp tục. Queue rejection được bắt/log và dùng TTL. Không durable queue, không CallerRunsPolicy. Config `@Bean(defaultCandidate=false)` + explicit qualifier giữ default applicationTaskExecutor của email/payment; test kiểm tra bean riêng và queue-full failure path.
5. **Không global SCAN cleanup:** DB AVAILABLE + Redis key không chứng minh key orphan: new writer có thể vừa SET trước DB CAS. Compare với snapshot đọc ngay trong sweeper cũng có thể xóa legitimate generation mới. Chỉ cleanup candidate đã biết trước write của request rollback hoặc snapshot booking đã commit; không tự thiết kế DB-token binding/schema/outbox khi chưa cần guarantee recovery tức thì.
6. **Deadline:** không thêm Redis SET/EXPIRE trong compensation; chỉ exact compare-delete. Same-owner retry/recovery đã có dùng Redis deadline hoặc DB live deadline PXAT, không now+900. Payment deadline vẫn createBooking time+15m. Retry đến sau expiry vẫn có thể thành lượt hold mới, không idempotency xuyên expiry.

Callback và queue không làm cross-store commit atomic. `beforeCommit` Redis read vẫn có cửa sổ tới actual DB commit; DB CAS bảo đảm seat ownership/status, afterCommit comparison không xóa newer UUID. Redis sau commit có thể chậm tới command timeout nhưng không được trả booking failure do cleanup.

### RED trước production change

`BookingRedisConsistencyIT` dùng PostgreSQL15.19/Redis7 Testcontainers thật, không outer test transaction. Mỗi booking có2 adult/2 seat/2 documents/2 meal add-ons để kiểm tra graph rollback đầy đủ. DB assertions qua JDBC connection riêng, không dựa managed entities. Interceptors chỉ pause/delegate real calls hoặc ném transport exception **sau Redis command đã chạy thật**.

| Core case | RED invariant violation | GREEN assertions |
| --- | --- | --- |
| Multi-seat late SQL failure | BEFORE INSERT trigger từ chối ticket2, key1 đã DEL | SQLState23514; zero6-table graph, cả2 DB HELD và exact keys/deadlines không đổi |
| Multi-seat second claim expires | Pause trước real claim2; expiry DB/Redis2 thật; key1 đã DEL | SEAT_NOT_AVAILABLE, first live lease preserved, second expired; full DB rollback |
| Booking actual commit failure | Deferred constraint trigger trên booking ném tại COMMIT, keys đã mất | SQLState23514, response null, zero graph, original leases/DB holds còn nguyên |
| Hold actual commit failure | Deferred trigger flight_seats UPDATE ném tại COMMIT, body catch không chạy | DB AVAILABLE/null metadata, no graph; candidates removed by rollback cleanup |
| Redis DEL denied | Real ACL `-del`: old DEL nằm trước commit, request fail/DB rollback, chỉ thử1 key | Committed booking response, count1 nhìn thấy trước DEL, attempts2; BOOKED/null hold; orphan keys giữ expiry gốc |
| Lost acquisition reply | Real SET NX thành công rồi test proxy ném RedisConnectionFailureException, map chưa biết lease | Original transport exception, DB AVAILABLE; pre-registered candidate được cleanup |
| Concurrent retry adopts same lease | Original pause sau SET; retry commit cùng UUID/deadline; original batch missing seat2 xóa valid lease | Original SEAT_NOT_AVAILABLE; DB retry HELD, exact key/deadline giữ nguyên |
| Rollback cleanup Redis failure | ACL-denied DEL che mất original SEAT_NOT_AVAILABLE | Giữ business error; DB rollback; retry không reset orphan deadline |

Commit failures là **PostgreSQL constraint trigger DEFERRABLE INITIALLY DEFERRED** trong container, không mock commit/save. Thời điểm này được PostgreSQL định nghĩa tại [SET CONSTRAINTS](https://www.postgresql.org/docs/15/sql-set-constraints.html). Test-only triggers/functions bị drop trước fixture cleanup; không thêm Flyway migration. ACL GET/DEL được restore trong finally/AfterEach. Reply-loss là explicit transport fault injection, không phải real TCP partition. Logs assertions che UUID.

RED đầu14:25:30 và confirmed14:31:30: **8 tests,8 failures,0 errors,0 skipped** trên production giữ nguyên. Confirmed run đã hoàn thiện helper assertion để BOOKED/null held_until được kiểm đúng và che raw lease trong report; không thay business expectation. Lỗi copy artifact path giữa hai run không khởi chạy Maven; không tính là test run.

### Coverage mở rộng và tính deterministic

8 follow-up invocations thêm sau core GREEN, không gọi chúng là RED trước fix:

- Late postCommit cleanup sau cancel + reacquire, **Bob và same-user Alice**: barrier trước real DEL khi DB đã commit, expire key cũ thật, new UUID/DB hold commit, resume old compare-delete → lease2/deadline/state giữ nguyên.
- BeforeCommit visibility: test synchronization pause, Future chưa done, independent JDBC thấy zero graph và Redis chưa consume; resume → committed graph đầy đủ và key consumed.
- Real Redis ACL GET failure ở beforeCommit sau2 claims: response null/rollback toàn graph, original leases/deadlines còn nguyên.
- Pending retry giữ row lock trong independent transaction; rollback cleanup NOWAIT trả **SQLState55P03**, PID khác; không chờ retry commit, không xóa shared lease. Cùng case chạy với max pool2 để chứng minh không cần extra reserved connection của caller.
- Crash-before-hold-commit state: Redis acquisition thật + independent PG transaction rollback/disconnect state, không transaction callbacks; DB AVAILABLE nhưng Redis orphan chặn Bob tới natural TTL, sau expiry Bob hold mới. **Không SIGKILL application process**.
- Post-commit orphan state từ actual Redis cleanup denial: BOOKED vẫn chặn Bob trước/sau key expiry và createBooking lần2 bằng documents khác, graph vẫn1 booking/2 tickets. Đây cũng là persisted state nếu crash sau commit trước callback.
- Queue-full actual AbortPolicy: fill1 active worker +128 queued no-op tasks, original SEAT_NOT_AVAILABLE vẫn trả về, Redis orphan giữ deadline≤900s, DB AVAILABLE/no graph; default applicationTaskExecutor không bị thay thế.

Deferred cleanup yêu cầu test chờ đúng transaction outcome: F04 capture lưu service result/exception trước, rồi FIFO barrier **sau khi proxy hoàn tất**. Assertions state/error/UUID/expiry giữ nguyên. Base IT drain queue trước truncate/flush; SeatHold batch và STC stale-writer thêm cùng barrier. RLC F03 booking mismatch pause trước compare-read sau claim (thay pre-commit delete), stale cleanup pause tại real compare-delete của compensation transaction; vẫn đo actual PG lock wait và giữ11 F03 +11 fixed-deadline cases. Không fake SQL/Redis results, không Thread.sleep, mọi waits/workers có timeout/finally; không disable/nới assertions.

### Commands, results và evidence

Tất cả Maven tại `backend/`, ngoài sandbox để Docker/Mockito hoạt động, Java22.0.2/release21; Flyway8 migrations. Không đổi pom/dependencies/CI/global Redis or Hikari settings; pool2 chỉ là test CLI override. Không gọi payOS thật.

```sh
./mvnw clean -Dit.test=BookingRedisConsistencyIT test-compile failsafe:integration-test failsafe:verify
./mvnw clean '-Dit.test=BookingRedisConsistencyIT,RedisLeaseConcurrencyIT,SeatTransitionConcurrencyIT' test-compile failsafe:integration-test failsafe:verify
./mvnw clean verify

# Pool RED command đầu không có clean; GREEN có clean như dưới
./mvnw clean '-Dit.test=BookingRedisConsistencyIT#rollbackCleanupDoesNotWaitForConcurrentRetryRowLock' -Dspring.datasource.hikari.maximum-pool-size=2 test-compile failsafe:integration-test failsafe:verify
```

| Stage | Tests | PASS | Failures | Errors | Skipped | Exit | Kết thúc2026-10-27 +07:00 / log |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| RED initial, trước sửa | 8 | 0 | 8 | 0 | 0 | 1 | 14:25:30; `/tmp/f04-red.log` |
| RED confirmed, trước sửa | 8 | 0 | 8 | 0 | 0 | 1 | 14:31:30; `/tmp/f04-red-confirmed.log` |
| GREEN core, synchronous rollback cleanup | 8 | 8 | 0 | 0 | 0 | 0 | 14:33:17; `/tmp/f04-green-core.log` |
| Expanded ban đầu: F04 14 + RLC22 + STC5 | 41 | 41 | 0 | 0 | 0 | 0 | 14:37:51; `/tmp/f04-green-expanded.log` |
| Full trước pool audit | 94 | 94 | 0 | 0 | 0 | 0 | 14:41:03; `/tmp/f04-clean-verify.log` |
| Pool2 phát hiện connection starvation | 1 | 0 | 0 | **1** | 0 | 1 | 14:43:42; `/tmp/f04-small-pool.log` |
| Pool2 sau deferred cleanup | 1 | 1 | 0 | 0 | 0 | 0 | 14:49:48; `/tmp/f04-small-pool-green.log` |
| **Final full clean verify** | **95** | **95** | **0** | **0** | **0** | **0** | **14:52:18**; `/tmp/f04-final-clean-verify.log` |

Pool error là TimeoutException ở Future.get5s; log sau interrupt cho thấy Hikari chờ connection, không phải invariant RED gốc hoặc lỗi discovery. Không che lỗi này bằng full94 PASS trước đó. **Final XML sau clean: Surefire33 + Failsafe62 =95 distinct cases**, failures/errors/skipped0/exit0; Failsafe62 = F04 16 + RLC22 + SeatHold3 + STC5 + BookingConcurrency3 + BookingInvariants12 + context1. Archive reports tại `/tmp/f04-final-surefire-reports` và `/tmp/f04-final-failsafe-reports`. F01/F02/fixed-deadline CAS và F03 generation regressions đều PASS. SQL23514/55P03/Redis ACL errors trong final GREEN là expected/checked negative paths. Không cộng repeated invocations thành distinct coverage; log `/tmp`/target có thể bị xóa nên evidence chính giữ ở đây.

### Failure modes còn lại và kết luận có giới hạn

| Mode | Safety / recovery contract | Giới hạn chưa chứng minh |
| --- | --- | --- |
| Managed rollback/multi-seat/commit rejection | PG rollback atomic, booking không consume Redis trước commit; known hold candidates được guarded cleanup | Không XA/atomic cross-store transaction |
| Post-commit Redis failure | Booking vẫn success/BOOKED, cleanup thử mọi exact snapshot, original TTL | Không durable retry job; response có thể chờ Redis timeout |
| Crash trước hold commit / Redis SET reply không biết | PG rollback khi session loss được xử lý; Redis key có finite original deadline; cleanup nếu callback sống | User khác có thể bị chặn tới TTL; process-kill/TCP partition chưa test |
| Crash sau Booking commit trước callback | PG BOOKED là authority; orphan tự expire, không reopen/double booking | Cancel sau đó có thể để key cũ chặn next hold tới TTL |
| Queue đầy/shutdown/lost background task | Log, bỏ cleanup an toàn, không sửa expiry hoặc mask original error | Availability dựa TTL; không retry loop/persistent queue |
| DB busy/pool contention | Callback trả connection; background fresh transaction + NOWAIT; live hold được giữ | Worker vẫn có thể chờ acquire connection dưới pool exhaustion, nhưng không giữ caller trong callback; DB session partition/locks kéo dài không có bound900s |
| Commit ACK lost / STATUS_UNKNOWN | Không đoán rollback để xóa valid lease; không tự báo booking success | DB có thể commit mà caller nhận lỗi; existing booking lookup cần dùng, chưa exactly-once HTTP/idempotency key |
| Redis failover/clock skew/Cluster | F01/F02 DB authority và compare UUID vẫn áp dụng trong tested runtime | Không chứng minh Redis durability/partition behavior; cần đồng bộ clocks |

Không scan current key để xóa/rewrite tự động vì thiếu durable generation binding; nếu sau này yêu cầu immediate crash reconciliation cần review schema/token binding hoặc durable job riêng. Không thêm outbox/Kafka/distributed transaction chỉ để thay thế TTL contract khi chưa có yêu cầu đó. Không kéo dài Fixed Hold Deadline trong bất kỳ cleanup/recovery path nào.

F01/F02 không bảo vệ mọi stale cancellation/payment DB writer: cancellation hiện dùng entity save, concurrency chuyên sâu chưa kiểm chứng. F04 chỉ giữ cancellation không xóa current Redis key và kiểm tra callback cũ sau cancel/reacquire; không tuyên bố toàn lifecycle safe. Chưa chạy JDK21 runtime/PostgreSQL17, stress mọi lock ordering, real app process death, network partitions hoặc full HTTP authorization.

Files F04: `BookingService`, `SeatHoldLeaseStore`, cleanup query `FlightSeatRepository`, mới `SeatHoldTransactionLifecycle`/`SeatHoldCleanupConfiguration`; mới `BookingRedisConsistencyIT`16 cases; base IT/SH/STC/RLC chỉ thêm cleanup barriers/timing delegation; status/audit. API/schema/Lua/deadlines/dependencies/payment production code không đổi. Giữ local changes có trước, không stage/commit/push. **F04 managed-transaction fixes được verify, crash/outage dùng bounded Redis-TTL fallback; Phase1 chưa đóng**, các gap F05–F10 và cancel/payment concurrency không tự triển khai.

## M. F05 Follow-up — GitHub Actions Maven Gate

Ngày **2026-10-29**, HEAD `ac8e030`; workflow change chưa commit/push. Scope chỉ audit/sửa CI workflow, không production business logic/tests/pom/dependencies.

### Findings và minimal change

- Workflow cũ chạy `mvn clean test` rồi `mvn package -DskipTests`: Surefire được gọi nhưng lifecycle không tới Failsafe integration-test/verify, nên không có IT gate dù pom đã đúng. Bước package riêng lặp lifecycle build.
- Đổi cùng bước có env/working-directory thành **`./mvnw clean verify`**. Verify đi qua package/Spring Boot repackage và Failsafe verify, nên bỏ package step riêng an toàn. Không thêm failure-ignore/skip flags hoặc continue-on-error.
- Giữ nguyên push/pull_request main/master + path filters, workflow_dispatch, ubuntu-latest, PostgreSQL17/Redis7 service containers/healthchecks/env/ports, checkout@v4, setup-java@v4 Temurin21/Maven cache, backend directory và dummy JWT/payOS variables. Workflow không có deploy/upload/publish step; task không thực thi deploy.
- Pom không cần sửa: Java21; Surefire includes **/*Test.java/excludes **/*IT.java; Failsafe includes **/*IT.java, goals integration-test và verify. Runtime plugins3.5.6, wrapper3.9.16. Failure Surefire làm Maven fail; Failsafe ghi failures ở integration-test rồi verify trả nonzero. Không thực hiện failure injection trong task; đây là configured/default semantics theo [Failsafe usage](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html), chưa có failed GitHub run để quan sát status thực tế.
- Testcontainers tạo PostgreSQL15/Redis7 riêng; dynamic properties override datasource/Redis host+ports cho IT. PostgreSQL17 workflow service không phải DB được IT verify. Giữ services theo yêu cầu, không tự xóa resource/function hiện có. `ubuntu-latest` phù hợp Linux service containers và Docker/Testcontainers theo [GitHub service-container requirements](https://docs.github.com/en/actions/tutorials/use-containerized-services/create-redis-service-containers); Docker socket actual trên runner vẫn cần run GitHub xác nhận.

### Local verification mới

1. Ruby Psych parse YAML AST thành công; semantic comparison chứng minh chỉ đổi test step name/run và bỏ duplicate package step, mọi job/triggers/services/setup/env còn lại giống snapshot đầu task.
2. Python XML inspection xác minh Surefire/Failsafe patterns/goals, Java21 alignment, không skip/ignore failures; wrapper tracked mode100755. So SHA256/HEAD xác nhận pom byte-for-byte không đổi. `git diff --check` scoped workflow PASS. Không có actionlint local, không claim actionlint check.
3. Docker info ngoài sandbox:29.2.0/Linux/aarch64; Docker Desktop thật. Java local22.0.2, compile release21; máy không có JDK21. Sandbox socket permission denied là environmental check ban đầu, không Maven failure; rerun được phép ngoài sandbox hoạt động.
4. `cd backend && ./mvnw clean verify` kết thúc **2026-10-29 00:32:48 +07:00**, exit0: **95 tests PASS, failures0/errors0/skipped0**. XML Surefire33 + Failsafe62, bao gồm16 F04,22 RLC (F03/fixed deadline), STC5, SH3, BC3, BI12 và context1. PostgreSQL15.19/Redis7 Testcontainers + Flyway8 migrations; không payOS thật. Không dùng số95 từ run trước làm kết quả mới.
5. `backend/target/backend-0.0.1-SNAPSHOT.jar` tồn tại và chứa BackendApplication tại BOOT-INF/classes cùng Spring Boot JarLauncher, xác minh packaging được giữ. Không chạy thêm package/Docker build/deploy.

Logs `/tmp/ci-local-clean-verify.log`; static checks `/tmp/ci-workflow-validation.json`; summary `/tmp/ci-local-verification.json`; snapshots `/tmp/ci-local-surefire-reports` và `/tmp/ci-local-failsafe-reports`.

### Chưa xác nhận trên GitHub

Chưa trigger hoặc đọc actual Actions run mới. Cần xác nhận checkout/setup/cache/wrapper network access, services/image pulls/Docker access, Temurin21 ubuntu-latest runtime, test discovery33+62 và failed-test → failed-job/required-status-check trên repo thật. Static/local PASS không chứng minh branch protection đã bắt buộc job này. Không thay branch protection, không push/commit/deploy; giữ Markdown local theo yêu cầu trước đó. F05 có implementation gate local, chưa tuyên bố CI enforcement hoàn tất trước actual run.


## N. Task 1 — Cancel / Payment Lifecycle BLOCKER (2026-10-29)

HEAD `f74423c`, local changes only; no commit/push/deploy. Scope: cancellation lifecycle, provider gateway mocked; no frontend/schema/dependency changes.

Root cause: BookingService.cancelBooking used an unlocked booking snapshot and left payment PENDING; webhook tested only payment state before issuing tickets; cancelPayment followed historical ticket seat references and unconditionally freed the current seat. A persistence context may retain snapshots across lock acquisition/three-phase payment creation.

Fix: Booking → Payment locking and explicit refresh of locked entities; cancelBooking atomically cancels booking/pending payment/tickets and records metadata. Cancelled booking cannot pass webhook success transition. Post-cancellation payment becomes PAID_AFTER_TERMINAL/REFUND_REQUIRED without ticket/seat changes. Repeated cancellation of cancelled booking's payment does not revisit allocations (also closes legacy pending intent). Conditional SQL releases only BOOKED seat with this booking's pending ticket and no other booking's active allocation; no managed FlightSeat overwrite or Redis mutation. Provider cancellation after booking commit; provider submit failure cannot turn committed cancellation into API failure. Payment create external call remains between transactions; locked snapshots refreshed for late attachment. F01–F04 original CAS/Lua/fixed deadlines unchanged.

`CancelPaymentLifecycleIT` uses real PostgreSQL/Redis Testcontainers, production booking/payment services and only a mocked external gateway. Independent JDBC assertions inspect committed states; four concurrency schedules use two workers/transactions, latch after real row lock, `pg_blocking_pids` proves loser waits on winner, bounded waits/finally shutdown. Tests include real deferred PostgreSQL commit rejection (SQLState23514), ownership rejection, legacy cancelled/pending payment with Bob's live lease preserved, stale pending ticket guarded release, post-commit provider rejection checked through independent JDBC connection. Signature/provider network are not tested.

Commands at backend:

```sh
./mvnw clean -Dit.test=CancelPaymentLifecycleIT test-compile failsafe:integration-test failsafe:verify
./mvnw clean verify
```

| Stage | Tests | Failures | Errors | Skipped | Exit | Evidence |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| RED confirmed, before source fix | 2 | 2 | 0 | 0 | 1 | /tmp/task1-red-confirmed.log |
| GREEN same core | 2 | 0 | 0 | 0 | 0 | /tmp/task1-green-core.log |
| Expanded | 8 | 0 | 0 | 0 | 0 | /tmp/task1-green-expanded.log |
| Expanded guarded release + commit failure | 10 | 0 | 0 | 0 | 0 | /tmp/task1-green-final.log |
| Final targeted | 12 | 0 | 0 | 0 | 0 | /tmp/task1-targeted.log |
| Full clean verify | 107 | 0 | 0 | 0 | 0 | /tmp/task1-full-verify.log |

RED asserted CANCELLED but observed COMPLETED; asserted BOOKED for new allocation but observed AVAILABLE. Initial RED attempt had an airport fixture error after base cleanup removed Flyway seed; fixture changed to create its own airports, then both business failures confirmed. Subsequent expanded tests use distinct Alice/Bob accounts. Full reports checked: Surefire33 + Failsafe74 =107 distinct invocations, including12 new cases; no aggregation of repeated runs. Java22.0.2/release21, PG15/Redis7, Flyway V1–V8; runtime JDK21/PG17 not verified.

Remaining limits: no durable provider-cancel retry/crash recovery; late real transfer requires refund handling. Full terminal/refund webhook idempotency (including REFUNDED overwrite), hardcoded QR, duplicate passenger concurrency and frontend recovery remain outside Task1. No claim of all payment scenarios/concurrency schedules proven. Unit cancellation test now verifies guarded release SQL invocation rather than managed FlightSeat mutation; actual persisted seat state is covered by new IT. Existing prior working-tree changes preserved.


## O. Task 2 — Webhook Idempotency / Terminal States / VND (2026-10-29)

Sau Task1, HIGH #3 không còn RED trên current source: Booking → Payment lock + explicit refresh đã ngăn stale HTTP persistence-context snapshot. Không bỏ refresh/booking guard để tạo bug giả. IT mới kiểm tra hai HTTP requests thật với OSIV (assert payment entity managed), cả hai preload snapshot trước lock; latch dưới actual transaction + pg_blocking_pids chứng minh row lock wait,2 PG PIDs độc lập. Test-only AFTER UPDATE trigger đếm1 committed payment write cho cả success và late-payment races. Success event counter đồng bộ xác nhận1 event sau transaction, JDBC/notification thực xác nhận side effects. No mock repository/transaction/persistence.

HIGH #4 còn RED: late callback lặp ghi metadata/updated_at và hạ REFUNDED về REFUND_REQUIRED. Fix trong PaymentServiceImpl: dưới existing locks/refresh, return no-op nếu SUCCESS/PAID_AFTER_TERMINAL hoặc refund_status != NONE. RefundStatus là chiều riêng với PaymentStatus; REFUNDED không phải một PaymentStatus. Không thêm refund API/provider workflow. Mọi terminal snapshot (status/refund/reference/paidAt/callbackData/updatedAt) được giữ nguyên. CANCELLED/EXPIRED+NONE vẫn chấp nhận first late receipt, không issue ticket hay reopen booking.

| Before | Callback | After / side effects |
| --- | --- | --- |
| Booking PENDING, Payment PENDING/NONE | on-time, exact payable VND | COMPLETED + SUCCESS/NONE + ISSUED +1 event |
| Payment PENDING/NONE | wrong amount or late | PAID_AFTER_TERMINAL/REFUND_REQUIRED, booking/tickets unchanged |
| Booking CANCELLED, payment CANCELLED/EXPIRED +NONE | first receipt | PAID_AFTER_TERMINAL/REFUND_REQUIRED; booking/tickets/seat unchanged |
| SUCCESS, any refund marker | repeated callback | no writes/events |
| PAID_AFTER_TERMINAL, any refund marker | repeated callback | no writes/events |
| Any payment with REFUND_REQUIRED/REFUNDED | old callback | preserve recorded financial result |

VND contract: [payOS API](https://payos.vn/docs/api/) requires integer amount. Existing outbound policy HALF_UP retained (project policy, not provider-mandated rounding). Shared payableVnd uses setScale(0,HALF_UP).longValueExact for request and webhook validation, including legacy decimal snapshots. Booking/payment stored decimal amounts/fare formula unchanged. Before fix .50/.55 requests sent rounded-up amount but webhook compared truncation, falsely requiring refund. Tests cover .00/.49/.50/.55 and wrong actual amount.

Commands at backend:

```sh
./mvnw clean -Dit.test=WebhookIdempotencyIT test-compile failsafe:integration-test failsafe:verify
./mvnw clean '-Dit.test=WebhookIdempotencyIT,CancelPaymentLifecycleIT' test-compile failsafe:integration-test failsafe:verify
./mvnw clean verify
```

| Stage | Tests | Pass | Failures | Errors | Skipped | Exit | Log |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Baseline confirmed on post-Task1 source |7|3|4|0|0|1|/tmp/task2-red-confirmed.log|
| GREEN core |7|7|0|0|0|0|/tmp/task2-green-core.log|
| Expanded webhook15 + Task1 12 |27|27|0|0|0|0|/tmp/task2-targeted-expanded.log|
| Full clean verify |122|122|0|0|0|0|/tmp/task2-full-verify.log|

Baseline confirmed: concurrent success already PASS;2 terminal snapshot cases and2 rounding cases FAIL. First baseline had an extra coordination assertion failure (PID measured outside transaction can reuse same pooled connection); corrected test to obtain PID under actual transaction, not counted as business RED. No weakened event/state assertions. Expanded15 includes success/late HTTP races, first CANCELLED/EXPIRED receipts, SUCCESS with refund markers, wrong amount, real deferred COMMIT rejection SQL23514/no event + successful retry. Refund completion simulated via JDBC fixture because no refund workflow is implemented. Notification listener remains real; subclass AfterEach waits boundedly for notifications before base fixture cleanup.

Full XML independently verified: Surefire33 + Failsafe89 =122, zero failures/errors/skipped. Includes Task1 12 and all F01–F04/fixed-deadline regressions. Runtime Java22.0.2/release21, real PostgreSQL15.19/Redis7/Flyway8; gateway mock only. No real provider calls/signature validation, JDK21/PG17 runtime or GitHub CI execution. No frontend/schema/dependency/deploy/commit/push.

Limitations: events/notifications are in-process post-commit delivery, not durable exactly-once across crash/listener failure. A different bank reference on an already terminal order is ignored while original record stays authoritative; genuine additional transfers need manual reconciliation/separate future requirements. No partial-payment/refund execution workflow added. Existing Task1/other local changes preserved. Task2 production delta after Task1 is only terminal guard + shared VND conversion in PaymentServiceImpl; new WebhookIdempotencyIT and documentation evidence.
