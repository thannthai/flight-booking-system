# Flight Booking System — API Design & Implementation Plan

## Nguyên tắc chung

- Base URL: `/api/v1`
- Response wrapper thống nhất:
```json
{
  "success": true,
  "message": "OK",
  "data": {},
  "timestamp": "2026-07-04T12:00:00Z"
}
```
- Error response:
```json
{
  "success": false,
  "message": "Seat already held",
  "errorCode": "SEAT_HELD",
  "timestamp": "2026-07-04T12:00:00Z"
}
```
- Auth: `Authorization: Bearer <access_token>` trên tất cả API cần login
- Refresh Token: HttpOnly Cookie (trình duyệt/Postman tự gửi)
- Phân quyền: `ROLE_USER`, `ROLE_ADMIN`, `ROLE_STAFF`
- Pagination: `?page=0&size=20&sort=createdAt,desc`

---

## Token Architecture

```
┌─────────────────────────────────────────────────────┐
│  CLIENT (Browser / Postman)                         │
│  Access Token  → RAM / Postman variable             │
│                  gắn vào header mỗi request         │
│  Refresh Token → HttpOnly Cookie                    │
│                  trình duyệt/Postman tự gửi         │
└────────────────────────┬────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│  BACKEND — Redis làm source of truth cho tokens     │
│                                                     │
│  "refresh:{tokenHash}" → userId   TTL 7 ngày        │
│  "blacklist:{jti}"     → true     TTL remaining     │
│                                                     │
│  Không cần bảng REFRESH_TOKENS trong PostgreSQL     │
└─────────────────────────────────────────────────────┘
```

| Token | Lưu phía client | Lưu phía server |
|---|---|---|
| Access Token | RAM / Postman variable | Không lưu — stateless JWT |
| Access Token blacklist | — | Redis `blacklist:{jti}` TTL 15ph |
| Refresh Token | HttpOnly Cookie | Redis `refresh:{hash}` TTL 7 ngày |

> **Lưu ý:** Redis đảm nhiệm hoàn toàn với TTL tự xóa, không cần scheduler dọn rác.

---

## Thứ tự code các module

```
1. Common (config, exception, response)     ← nền tảng, làm trước hết
2. Auth                                     ← mọi thứ phụ thuộc vào đây
3. Master Data (Airport, Airline, Aircraft) ← seed data cần có
4. Flight + Flight Seats                    ← core search
5. Booking + Seat Hold (Redis)              ← core business
6. Payment (Stripe/Sepay + Webhook)         ← chốt đơn
7. Ticket                                   ← issue sau payment
8. Notification (@Async email)              ← fire-and-forget
9. Scheduler                                ← background jobs
10. Voucher                                 ← optional, thêm sau
11. Admin APIs                              ← quản lý + audit
```

---

## Module 1 — Common Setup

### Không có API, chỉ có config

**Các class cần tạo:**

```
common/
├── config/
│   ├── SecurityConfig.java         # permit list, filter chain, CORS
│   ├── RedisConfig.java            # RedisTemplate bean
│   ├── AsyncConfig.java            # @EnableAsync, thread pool
│   └── OpenApiConfig.java          # Swagger JWT header
├── response/
│   └── ApiResponse.java            # wrapper generic
├── exception/
│   ├── AppException.java           # custom runtime exception
│   ├── ErrorCode.java              # enum tất cả error codes
│   └── GlobalExceptionHandler.java # @RestControllerAdvice
└── util/
    └── PNRGenerator.java           # random 6-char uppercase
```

**ErrorCode enum:**
```java
public enum ErrorCode {
    USER_NOT_FOUND(404, "User not found"),
    EMAIL_EXISTED(409, "Email already exists"),
    INVALID_CREDENTIALS(401, "Invalid email or password"),
    TOKEN_EXPIRED(401, "Token expired"),
    TOKEN_INVALID(401, "Token invalid"),
    TOKEN_BLACKLISTED(401, "Token has been revoked"),
    REFRESH_TOKEN_INVALID(401, "Refresh token invalid or expired"),
    SEAT_NOT_AVAILABLE(409, "Seat is not available"),
    SEAT_HELD(409, "Seat is currently held by another user"),
    BOOKING_NOT_FOUND(404, "Booking not found"),
    FLIGHT_NOT_FOUND(404, "Flight not found"),
    PAYMENT_ALREADY_PROCESSED(409, "Payment already processed"),
    VOUCHER_EXPIRED(400, "Voucher has expired"),
    VOUCHER_EXCEEDED(409, "Voucher usage limit exceeded"),
    FORBIDDEN(403, "Access denied"),
    INTERNAL_ERROR(500, "Internal server error");
}
```

**Redis key patterns:**
```
refresh:{sha256(refreshToken)}  → userId string   TTL = 7 days
blacklist:{jti}                 → "true" string   TTL = remaining access token TTL
hold:seat:{seatId}:user:{userId} → "held" string  TTL = 15 minutes
```

---

## Module 2 — Auth

### Entities cần tạo
- `User` → map `"USERS"` (không có `REFRESH_TOKENS` nữa)

### Redis keys dùng trong module này
```
SET refresh:{sha256(token)}  {userId}  EX 604800   ← login / refresh
DEL refresh:{sha256(token)}                         ← logout
SET blacklist:{jti}          "true"    EX {ttl}     ← logout
GET refresh:{sha256(token)}                         ← validate
```

### APIs

#### POST `/auth/register`
- **Auth:** Public
- **Request:**
```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "fullName": "Nguyen Van A",
  "phone": "0901234567"
}
```
- **Response:** `201`
```json
{
  "userId": 1,
  "email": "user@example.com",
  "fullName": "Nguyen Van A"
}
```
- **Logic:**
  1. Check email tồn tại → throw `EMAIL_EXISTED`
  2. BCrypt hash password
  3. Save User với `role = USER`

---

#### POST `/auth/login`
- **Auth:** Public
- **Request:**
```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```
- **Response:** `200`
  - Body:
  ```json
  {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
  ```
  - Header: `Set-Cookie: refreshToken={uuid}; HttpOnly; Secure; Path=/api/v1/auth; SameSite=Strict; Max-Age=604800`
- **Logic:**
  1. Load user by email → BCrypt matches → throw `INVALID_CREDENTIALS` nếu sai
  2. Generate access token JWT (claims: userId, role, jti, exp=15ph)
  3. Generate refresh token = UUID.randomUUID()
  4. Hash refresh token: `sha256(refreshToken)`
  5. `SET Redis "refresh:{hash}" {userId} EX 604800`
  6. Set HttpOnly Cookie chứa raw refresh token
  7. Trả access token trong body

---

#### POST `/auth/refresh`
- **Auth:** Public — đọc refresh token từ Cookie
- **Request:** Không cần body (Cookie tự gửi)
- **Response:** `200`
  - Body: `{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900 }`
  - Header: Set-Cookie với refresh token mới (rotation)
- **Logic:**
  1. Đọc refresh token từ Cookie `refreshToken`
  2. Hash → `GET Redis "refresh:{hash}"`
  3. Nếu null → throw `REFRESH_TOKEN_INVALID`
  4. Load userId từ Redis value
  5. **Rotation:**
     - `DEL Redis "refresh:{oldHash}"`
     - Generate refresh token mới → hash → `SET Redis "refresh:{newHash}" {userId} EX 604800`
     - Set Cookie mới
  6. Generate access token mới
  7. Trả về

---

#### POST `/auth/logout`
- **Auth:** Bearer token
- **Request:** Không cần body
- **Response:** `200`
- **Logic:**
  1. Extract `jti` và `exp` từ access token trong header
  2. Tính remaining TTL = `exp - now`
  3. `SET Redis "blacklist:{jti}" "true" EX {remainingTtl}`
  4. Đọc refresh token từ Cookie → hash → `DEL Redis "refresh:{hash}"`
  5. Clear Cookie: `Set-Cookie: refreshToken=; Max-Age=0`

---

#### GET `/auth/me`
- **Auth:** Bearer token
- **Response:** `200`
```json
{
  "id": 1,
  "email": "user@example.com",
  "fullName": "Nguyen Van A",
  "phone": "0901234567",
  "role": "USER"
}
```

---

#### JWT Filter logic (JwtAuthFilter)
```
Mỗi request đến:
1. Extract token từ header "Authorization: Bearer {token}"
2. Verify chữ ký JWT
3. Check exp chưa hết hạn
4. GET Redis "blacklist:{jti}" → nếu tồn tại → 401 TOKEN_BLACKLISTED
5. Load UserDetails → set SecurityContext
```

---

### Các class cần tạo
```
auth/
├── entity/
│   └── User.java
├── repository/
│   └── UserRepository.java
├── dto/
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── AuthResponse.java
│   └── UserResponse.java
├── service/
│   ├── AuthService.java
│   └── JwtService.java
├── controller/
│   └── AuthController.java
└── security/
    ├── JwtAuthFilter.java
    ├── UserDetailsServiceImpl.java
    └── CustomUserDetails.java
```

---

## Module 3 — Master Data

### 3A. Airport

#### APIs

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/airports` | Public | Danh sách sân bay (dùng cho dropdown) |
| GET | `/airports/{id}` | Public | Chi tiết |
| POST | `/admin/airports` | ADMIN | Tạo |
| PUT | `/admin/airports/{id}` | ADMIN | Sửa |
| DELETE | `/admin/airports/{id}` | ADMIN | Xóa — check FK |

**GET `/airports` Response:**
```json
[
  {
    "id": 1,
    "iataCode": "SGN",
    "name": "Sân bay Quốc tế Tân Sơn Nhất",
    "city": "Hồ Chí Minh",
    "country": "Vietnam"
  }
]
```

**POST `/admin/airports` Request:**
```json
{
  "iataCode": "SGN",
  "name": "Sân bay Quốc tế Tân Sơn Nhất",
  "city": "Hồ Chí Minh",
  "country": "Vietnam",
  "latitude": 10.8185,
  "longitude": 106.6655
}
```

---

### 3B. Airline

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/airlines` | Public | Danh sách |
| GET | `/airlines/{id}` | Public | Chi tiết |
| POST | `/admin/airlines` | ADMIN | Tạo |
| PUT | `/admin/airlines/{id}` | ADMIN | Sửa |
| DELETE | `/admin/airlines/{id}` | ADMIN | Xóa — check active flights |

**POST `/admin/airlines` Request:**
```json
{
  "code": "VJ",
  "name": "VietJet Air",
  "logoUrl": "https://...",
  "freeBaggage": false,
  "extraBaggageFee": 150000
}
```

---

### 3C. Aircraft

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/aircraft` | Public | Danh sách |
| GET | `/aircraft/{id}` | Public | Chi tiết |
| POST | `/admin/aircraft` | ADMIN | Tạo |
| PUT | `/admin/aircraft/{id}` | ADMIN | Sửa |
| DELETE | `/admin/aircraft/{id}` | ADMIN | Xóa |

**POST `/admin/aircraft` Request:**
```json
{
  "airlineId": 1,
  "model": "Airbus A321",
  "totalSeats": 180,
  "businessSeats": 30,
  "economySeats": 150,
  "registrationNo": "VN-A321"
}
```

---

### Seed Data

File `V2__seed_data.sql`:
```sql
INSERT INTO "AIRPORTS" (id, iata_code, name, city, country, created_at) VALUES
(1, 'SGN', 'Tân Sơn Nhất', 'Hồ Chí Minh', 'Vietnam', NOW()),
(2, 'HAN', 'Nội Bài', 'Hà Nội', 'Vietnam', NOW()),
(3, 'DAD', 'Đà Nẵng', 'Đà Nẵng', 'Vietnam', NOW()),
(4, 'CXR', 'Cam Ranh', 'Khánh Hòa', 'Vietnam', NOW()),
(5, 'PQC', 'Phú Quốc', 'Kiên Giang', 'Vietnam', NOW());

INSERT INTO "AIRLINES" (id, code, name, free_baggage, extra_baggage_fee, created_at) VALUES
(1, 'VN', 'Vietnam Airlines', true,  200000, NOW()),
(2, 'VJ', 'VietJet Air',      false, 150000, NOW()),
(3, 'QH', 'Bamboo Airways',   true,  180000, NOW());

INSERT INTO "AIRCRAFTS" (id, airline_code_id, model, total_seats, business_seats, economy_seats, registration_no, created_at) VALUES
(1, 1, 'Airbus A321', 180, 30,  150, 'VN-A321-01', NOW()),
(2, 2, 'Airbus A320', 160,  0,  160, 'VJ-A320-01', NOW()),
(3, 3, 'Boeing 787',  280, 40,  240, 'QH-787-01',  NOW());
```

---

## Module 4 — Flight & Flight Seats

### Entities
- `Flight` → map `"FLIGHTS"`
- `FlightSeat` → map `"FLIGHT_SEATS"`

### APIs

#### GET `/flights/search`
- **Auth:** Public
- **Query params:** `from`, `to`, `departureDate`, `cabinClass` (ECONOMY/BUSINESS)
- **Response:**
```json
[
  {
    "id": 1,
    "flightNo": "VN123",
    "airline": { "code": "VN", "name": "Vietnam Airlines", "logoUrl": "..." },
    "origin": { "iataCode": "SGN", "city": "Hồ Chí Minh" },
    "destination": { "iataCode": "HAN", "city": "Hà Nội" },
    "departureTime": "2026-07-10T08:00:00+07:00",
    "arrivalTime": "2026-07-10T10:10:00+07:00",
    "duration": "2h10m",
    "basePrice": 890000,
    "availableSeats": 45,
    "status": "SCHEDULED"
  }
]
```
- **Logic:**
  1. Query FLIGHTS JOIN AIRPORTS JOIN AIRLINES
  2. Filter: `origin_iata_id`, `dest_iata_id`, `DATE(departure_time) = departureDate`, `status = SCHEDULED`
  3. Count available: `WHERE status='AVAILABLE' OR (status='HELD' AND held_until < NOW())`

---

#### GET `/flights/{id}`
- **Auth:** Public
- **Response:** Chi tiết chuyến bay đầy đủ

---

#### GET `/flights/{id}/seats`
- **Auth:** Bearer token (USER)
- **Response:**
```json
{
  "flightId": 1,
  "aircraft": { "model": "A321", "totalSeats": 180 },
  "seats": [
    { "id": 1, "seatNo": "A1", "cabinClass": "BUSINESS", "price": 2500000, "status": "AVAILABLE" },
    { "id": 2, "seatNo": "A2", "cabinClass": "BUSINESS", "price": 2500000, "status": "HELD" }
  ]
}
```
- **Logic:** Lazy check — `status='HELD' AND held_until < NOW()` → hiển thị AVAILABLE

---

#### POST `/admin/flights`
- **Auth:** ADMIN
- **Request:**
```json
{
  "flightNo": "VN123",
  "airlineId": 1,
  "aircraftId": 1,
  "originAirportId": 1,
  "destAirportId": 2,
  "departureTime": "2026-07-10T08:00:00+07:00",
  "arrivalTime": "2026-07-10T10:10:00+07:00",
  "basePrice": 890000,
  "source": "MANUAL"
}
```
- **Logic:**
  1. Save FLIGHTS
  2. Load aircraft → `businessSeats`, `economySeats`
  3. Loop sinh FLIGHT_SEATS:
     - Business: A1→A{n}, price = basePrice × 2.5
     - Economy: B1→B{n}, price = basePrice
  4. Bulk insert FLIGHT_SEATS

---

#### PUT `/admin/flights/{id}/status`
- **Auth:** ADMIN
- **Request:** `{ "status": "DELAYED", "newDepartureTime": "..." }`
- **Logic:**
  1. Update flight status
  2. Nếu DELAYED / CANCELLED → publish `FlightStatusChangedEvent`

---

#### GET `/admin/flights`
- **Auth:** ADMIN
- **Query:** `?page=0&size=20&status=SCHEDULED`

---

## Module 5 — Booking (Core)

### Entities
- `Booking` → map `"BOOKING"`
- `BookingLeg` → map `"BOOKING_LEGS"`
- `Ticket` → map `"TICKETS"`
- `AddOnService` → map `"ADD_ON_SERVICES"`
- `Passenger` → map `"PASSENGERS"`
- `PassengerDocument` → map `"PASSENGER_DOCUMENTS"`

### Redis keys dùng trong module này
```
SET hold:seat:{seatId}:user:{userId}  "held"  EX 900   ← hold
DEL hold:seat:{seatId}:user:{userId}                    ← confirm / expire
```

### APIs

#### POST `/bookings/hold-seats`
- **Auth:** Bearer token (USER)
- **Request:**
```json
{
  "seats": [
    { "flightSeatId": 1 },
    { "flightSeatId": 2 }
  ]
}
```
- **Response:**
```json
{
  "heldSeats": [1, 2],
  "expiresAt": "2026-07-04T12:15:00+07:00",
  "ttlSeconds": 900
}
```
- **Logic:**
  1. Với mỗi seatId — atomic UPDATE:
     ```sql
     UPDATE FLIGHT_SEATS
     SET status = 'HELD',
         held_by_user = ?,
         held_until = NOW() + INTERVAL '15 minutes'
     WHERE id = ?
       AND (status = 'AVAILABLE'
         OR (status = 'HELD' AND held_until < NOW()))
     ```
  2. Nếu 0 rows updated → throw `SEAT_NOT_AVAILABLE`
  3. `SET Redis "hold:seat:{id}:user:{userId}" "held" EX 900`
  4. Trả danh sách ghế đã hold + expiresAt

---

#### POST `/bookings`
- **Auth:** Bearer token (USER)
- **Request:**
```json
{
  "tripType": "ONE_WAY",
  "legs": [
    {
      "flightId": 1,
      "direction": "OUTBOUND",
      "legOrder": 1,
      "passengers": [
        {
          "fullName": "Nguyen Van A",
          "dateOfBirth": "1990-01-01",
          "nationality": "Vietnam",
          "passengerType": "ADULT",
          "identityType": "CCCD",
          "identityNumber": "001234567890",
          "seatId": 1,
          "addOns": [
            { "serviceType": "BAGGAGE", "description": "20kg", "price": 200000, "quantity": 1 }
          ]
        }
      ]
    }
  ],
  "voucherId": null
}
```
- **Response:** `201`
```json
{
  "bookingId": 1,
  "pnr": "ABC123",
  "status": "PENDING",
  "baseAmount": 890000,
  "taxAmount": 89000,
  "discountAmount": 0,
  "totalAmount": 979000,
  "expiresAt": "2026-07-04T12:15:00+07:00"
}
```
- **Logic:**
  1. Verify tất cả ghế đang HELD bởi chính user này
  2. Tính giá theo loại hành khách:
     - ADULT: 100% × seat.price
     - CHILD: 75% × seat.price
     - INFANT: 150,000 fixed (không chiếm ghế)
  3. tax = base × 10%
  4. Apply voucher nếu có (Optimistic Lock `@Version`)
  5. Generate PNR (6 ký tự uppercase, unique)
  6. Save BOOKING (status = PENDING)
  7. Save BOOKING_LEGS
  8. Save/lookup PASSENGERS + PASSENGER_DOCUMENTS
  9. Save TICKETS (ticket_no = null, status = ISSUED sau payment)
  10. Save ADD_ON_SERVICES
  11. Publish `BookingCreatedEvent` → BOOKING_CONFIRM email

---

#### GET `/bookings/{id}`
- **Auth:** Bearer token (USER xem của mình, ADMIN xem tất cả)
- **Response:** Chi tiết booking với legs, tickets, payments

---

#### GET `/bookings/my`
- **Auth:** Bearer token (USER)
- **Query:** `?page=0&size=10&status=COMPLETED`

---

#### POST `/bookings/{id}/cancel-request`
- **Auth:** Bearer token (USER)
- **Request:** `{ "reason": "Thay đổi kế hoạch" }`
- **Logic:**
  1. Verify booking thuộc về user
  2. Check status = COMPLETED
  3. Set status = PENDING_CANCEL

---

## Module 6 — Payment

### Entity: `Payment` → map `"PAYMENTS"`

### APIs

#### POST `/payments/create`
- **Auth:** Bearer token (USER)
- **Request:**
```json
{ "bookingId": 1, "provider": "STRIPE" }
```
- **Response:**
```json
{
  "paymentUrl": "https://checkout.stripe.com/pay/cs_test_xxx",
  "provider": "STRIPE",
  "amount": 979000,
  "currency": "VND"
}
```
- **Logic:**
  1. Load booking, verify status = PENDING, thuộc về user
  2. Verify ghế vẫn trong thời gian hold
  3. Tạo Stripe checkout session với `metadata: { bookingId }`
  4. Save PAYMENT (status = PENDING, provider_txn_id = session_id)
  5. Trả payment URL

---

#### POST `/payments/webhook`
- **Auth:** Public — verify Stripe signature
- **Logic:**
  1. Verify `Stripe-Signature` header
  2. Extract `provider_txn_id`
  3. **Idempotency:** `SELECT WHERE provider_txn_id = ? AND status = 'SUCCESS'` → nếu đã xử lý → return 200
  4. Nếu `payment_intent.succeeded`:
     - UPDATE PAYMENT: status = SUCCESS, paid_at = NOW()
     - UPDATE BOOKING: status = COMPLETED
     - UPDATE FLIGHT_SEATS: status = BOOKED, clear hold fields
     - `DEL Redis "hold:seat:{id}:user:{userId}"`
     - Generate ticket_no: `{airline}-{PNR}-{legOrder}-{passengerIndex}`
     - UPDATE TICKETS: status = ISSUED, ticket_no, issued_at = NOW()
     - Publish `PaymentSuccessEvent` → TICKET_ISSUED email
  5. Nếu `payment_intent.payment_failed`:
     - UPDATE PAYMENT: status = FAILED
     - UPDATE BOOKING: status = CANCELLED
     - UPDATE FLIGHT_SEATS: status = AVAILABLE, clear hold fields
     - `DEL Redis hold keys`

---

#### GET `/payments/{bookingId}/status`
- **Auth:** Bearer token (USER)

---

## Module 7 — Notification

### Entity: `Notification` → map `"NOTIFICATIONS"`
### Không có public API — chỉ có internal event listeners

```
notification/
├── entity/        Notification.java
├── repository/    NotificationRepository.java
├── event/
│   ├── BookingCreatedEvent.java
│   ├── PaymentSuccessEvent.java
│   ├── FlightStatusChangedEvent.java
│   └── PaymentReminderEvent.java
├── listener/      NotificationEventListener.java   # @Async @EventListener
├── service/       EmailService.java
└── template/
    ├── booking-confirm.html
    └── ticket-issued.html
```

**6 email types:**

| Event | Email type | Trigger |
|---|---|---|
| BookingCreatedEvent | BOOKING_CONFIRM | POST /bookings |
| PaymentSuccessEvent | TICKET_ISSUED | Webhook SUCCESS |
| PaymentReminderEvent | PAYMENT_REMINDER | Scheduler 10ph |
| FlightStatusChangedEvent (DELAYED) | FLIGHT_DELAYED | PUT /admin/flights/{id}/status |
| FlightStatusChangedEvent (CANCELLED) | FLIGHT_CANCELLED | PUT /admin/flights/{id}/status |
| RefundCompletedEvent | REFUND_CONFIRMATION | PUT /admin/bookings/{id}/refund |

---

## Module 8 — Scheduler

```java
@Component
public class BookingScheduler {

    // Job 1: Release held seats — mỗi 1 phút
    @Scheduled(fixedDelay = 60_000)
    public void releaseExpiredHeldSeats() {
        // UPDATE FLIGHT_SEATS SET status='AVAILABLE', held_until=NULL, held_by_user=NULL
        // WHERE status='HELD' AND held_until < NOW()
    }

    // Job 2: Payment reminder — mỗi 5 phút
    @Scheduled(fixedDelay = 300_000)
    public void sendPaymentReminders() {
        // SELECT bookings WHERE status='PENDING'
        //   AND created_at < NOW() - INTERVAL '10 minutes'
        //   AND reminder_sent = false
        // → publish PaymentReminderEvent
        // → UPDATE reminder_sent = true
    }
}
```

> Cần thêm migration `V3__add_columns.sql`:
> ```sql
> ALTER TABLE "BOOKING" ADD COLUMN "reminder_sent" BOOLEAN NOT NULL DEFAULT false;
> ```

---

## Module 9 — Voucher (Optional)

### APIs

#### POST `/vouchers/validate`
- **Auth:** Bearer token (USER)
- **Request:** `{ "code": "SUMMER26", "bookingAmount": 1000000 }`
- **Response:**
```json
{
  "voucherId": 1,
  "discountType": "PERCENT",
  "value": 10,
  "discountAmount": 100000,
  "finalAmount": 900000
}
```
- **Logic:** Validate code, dates, usage_limit — chưa tăng used_count (chỉ tăng khi booking confirmed)

| Method | Path | Auth |
|---|---|---|
| POST | `/vouchers/validate` | USER |
| GET | `/admin/vouchers` | ADMIN |
| POST | `/admin/vouchers` | ADMIN |
| PUT | `/admin/vouchers/{id}` | ADMIN |

---

## Module 10 — Admin APIs

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/admin/bookings` | ADMIN | List, filter theo status/date |
| GET | `/admin/bookings/{pnr}` | ADMIN | Tra cứu PNR |
| PUT | `/admin/bookings/{id}/cancel` | ADMIN | Hủy vé + ghi AUDIT_LOGS |
| PUT | `/admin/bookings/{id}/refund` | ADMIN | Đánh dấu hoàn tiền |

**PUT `/admin/bookings/{id}/cancel` Logic:**
1. UPDATE BOOKING status = CANCELLED
2. UPDATE TICKETS status = CANCELLED
3. UPDATE FLIGHT_SEATS status = AVAILABLE
4. Ghi AUDIT_LOGS (before/after JSON snapshot)
5. Publish notification event

### Passenger (User tự quản lý)

| Method | Path | Auth |
|---|---|---|
| GET | `/users/me/passengers` | USER |
| POST | `/users/me/passengers` | USER |
| PUT | `/users/me/passengers/{id}` | USER |
| DELETE | `/users/me/passengers/{id}` | USER |

---

## Tóm tắt tất cả API

### Public
```
POST /auth/register
POST /auth/login
POST /auth/refresh        ← đọc cookie tự động
GET  /airports
GET  /airlines
GET  /flights/search
GET  /flights/{id}
POST /payments/webhook
```

### USER (Bearer token)
```
POST   /auth/logout
GET    /auth/me
GET    /flights/{id}/seats
POST   /bookings/hold-seats
POST   /bookings
GET    /bookings/{id}
GET    /bookings/my
POST   /bookings/{id}/cancel-request
POST   /payments/create
GET    /payments/{bookingId}/status
GET    /users/me/passengers
POST   /users/me/passengers
PUT    /users/me/passengers/{id}
DELETE /users/me/passengers/{id}
POST   /vouchers/validate
```

### ADMIN
```
POST   /admin/airports
PUT    /admin/airports/{id}
DELETE /admin/airports/{id}
POST   /admin/airlines
PUT    /admin/airlines/{id}
DELETE /admin/airlines/{id}
POST   /admin/aircraft
PUT    /admin/aircraft/{id}
DELETE /admin/aircraft/{id}
POST   /admin/flights
PUT    /admin/flights/{id}/status
GET    /admin/flights
GET    /admin/bookings
GET    /admin/bookings/{pnr}
PUT    /admin/bookings/{id}/cancel
PUT    /admin/bookings/{id}/refund
GET    /admin/vouchers
POST   /admin/vouchers
PUT    /admin/vouchers/{id}
```

---

## Package Structure

```
com.thanh.backend/
├── auth/
│   ├── entity/         User.java
│   ├── repository/     UserRepository.java
│   ├── dto/            RegisterRequest, LoginRequest, AuthResponse, UserResponse
│   ├── service/        AuthService, JwtService
│   ├── controller/     AuthController
│   └── security/       JwtAuthFilter, UserDetailsServiceImpl, CustomUserDetails
│
├── airport/
│   ├── entity/         Airport
│   ├── repository/     AirportRepository
│   ├── dto/            AirportRequest, AirportResponse
│   ├── service/        AirportService
│   └── controller/     AirportController
│
├── airline/            (tương tự Airport)
│
├── aircraft/           (tương tự Airport)
│
├── flight/
│   ├── entity/         Flight, FlightSeat
│   ├── repository/     FlightRepository, FlightSeatRepository
│   ├── dto/            FlightSearchRequest, FlightResponse, SeatResponse
│   ├── service/        FlightService, FlightSeatService
│   └── controller/     FlightController
│
├── booking/
│   ├── entity/         Booking, BookingLeg, Ticket, AddOnService
│   ├── repository/     BookingRepository, BookingLegRepository,
│   │                   TicketRepository, AddOnServiceRepository
│   ├── dto/            CreateBookingRequest, HoldSeatsRequest, BookingResponse
│   ├── service/        BookingService, SeatHoldService
│   └── controller/     BookingController
│
├── passenger/
│   ├── entity/         Passenger, PassengerDocument
│   ├── repository/     PassengerRepository, PassengerDocumentRepository
│   ├── dto/            PassengerRequest, PassengerResponse
│   ├── service/        PassengerService
│   └── controller/     PassengerController
│
├── payment/
│   ├── entity/         Payment
│   ├── repository/     PaymentRepository
│   ├── dto/            CreatePaymentRequest, PaymentResponse
│   ├── service/        PaymentService, StripeService
│   └── controller/     PaymentController
│
├── notification/
│   ├── entity/         Notification
│   ├── repository/     NotificationRepository
│   ├── event/          BookingCreatedEvent, PaymentSuccessEvent,
│   │                   FlightStatusChangedEvent, PaymentReminderEvent
│   ├── listener/       NotificationEventListener
│   └── service/        EmailService
│
├── voucher/
│   ├── entity/         Voucher
│   ├── repository/     VoucherRepository
│   ├── dto/            VoucherValidateRequest, VoucherResponse
│   ├── service/        VoucherService
│   └── controller/     VoucherController
│
├── admin/
│   └── controller/     AdminBookingController, AdminFlightController
│
├── scheduler/
│   └── BookingScheduler.java
│
└── common/
    ├── config/         SecurityConfig, RedisConfig, AsyncConfig, OpenApiConfig
    ├── response/       ApiResponse
    ├── exception/      AppException, ErrorCode, GlobalExceptionHandler
    └── util/           PNRGenerator
```

---

## Migration Files

```
V1__init.sql              ← schema đã fix FK (không có REFRESH_TOKENS)
V2__seed_data.sql         ← airports, airlines, aircrafts mẫu
V3__add_columns.sql       ← ALTER TABLE "BOOKING" ADD COLUMN "reminder_sent" BOOLEAN NOT NULL DEFAULT false;
```

---

## Redis Key Summary

```
refresh:{sha256(token)}          → "{userId}"    TTL 7 days    Auth module
blacklist:{jti}                  → "true"        TTL remaining Auth module
hold:seat:{seatId}:user:{userId} → "held"        TTL 15 min    Booking module
```
