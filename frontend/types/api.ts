/**
 * api.ts — Frontend TypeScript types
 *
 * Every type here is derived directly from the Spring Boot backend DTOs and enums.
 * No fields are invented. Where backend contract is unclear, a TODO comment explains what
 * needs to be reconciled before connecting to the real API.
 *
 * Source of truth: /backend/src/main/java/com/thanh/backend/
 */

// ---------------------------------------------------------------------------
// Unified API response wrapper
// Matches: com.thanh.backend.common.response.ApiResponse<T>
// ---------------------------------------------------------------------------

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ApiErrorResponse {
  success: false;
  message: string;
  errorCode: string;
  timestamp: string;
}

// ---------------------------------------------------------------------------
// Enums — Auth module
// Source: com.thanh.backend.auth.enums.*
// ---------------------------------------------------------------------------

export type UserRole = "ROLE_USER" | "ROLE_ADMIN";

// ---------------------------------------------------------------------------
// Enums — Flight module
// Source: com.thanh.backend.flight.enums.*
// ---------------------------------------------------------------------------

export type CabinClass = "ECONOMY" | "BUSINESS";

export type SeatStatus = "AVAILABLE" | "HELD" | "BOOKED";

export type FlightStatus = "SCHEDULED" | "DELAYED" | "CANCELLED" | "COMPLETED";

export type FlightSource = "MANUAL" | "DEMO";

// ---------------------------------------------------------------------------
// Enums — Booking module
// Source: com.thanh.backend.booking.enums.*
// ---------------------------------------------------------------------------

export type TripType = "ONE_WAY" | "ROUND_TRIP";

export type BookingStatus =
  | "PENDING"
  | "COMPLETED"
  | "CANCELLED"
  | "PENDING_CANCEL"
  | "REFUNDED";

export type PassengerType = "ADULT" | "CHILD" | "INFANT";

export type TicketStatus = "PENDING" | "ISSUED" | "CANCELLED";

export type AddOnType = "BAGGAGE" | "INSURANCE" | "MEAL";

export type DocumentType = "PASSPORT" | "CITIZEN_ID";

export type Gender = "MALE" | "FEMALE" | "OTHER";

// ---------------------------------------------------------------------------
// Enums — Payment module
// Source: com.thanh.backend.payment.enums.*
// ---------------------------------------------------------------------------

export type PaymentStatus =
  | "PENDING"
  | "SUCCESS"
  | "CANCELLED"
  | "EXPIRED"
  | "PAID_AFTER_TERMINAL";

export type RefundStatus = "NONE" | "REFUND_REQUIRED" | "REFUNDED";

// ---------------------------------------------------------------------------
// Response types — Auth module
// Source: com.thanh.backend.auth.dto.response.*
// ---------------------------------------------------------------------------

export interface UserResponse {
  demoAlias?: 'A' | 'B' | null;
  id: number;
  email: string;
  fullName: string;
  phone: string;
  role: UserRole;
  createdAt: string; // OffsetDateTime serialised as ISO-8601
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number; // seconds
  user: UserResponse;
}

// ---------------------------------------------------------------------------
// Response types — Master Data (Airport, Airline, Aircraft)
// Source: com.thanh.backend.airport/airline/aircraft dto/response
// ---------------------------------------------------------------------------

export interface AirportResponse {
  id: number;
  iataCode: string;
  name: string;
  city: string;
  country: string;
  timezone?: string;
  latitude?: number;
  longitude?: number;
  createdAt: string;
  updatedAt: string;
}

export interface AirlineResponse {
  id: number;
  code: string;
  name: string;
  logoUrl?: string;
  freeBaggage: boolean;
  extraBaggageFee: number; // BigDecimal -> number
  createdAt: string;
  updatedAt: string;
}

export interface AircraftResponse {
  id: number;
  airline: AirlineResponse;
  model: string;
  totalSeats: number;
  businessSeats: number;
  economySeats: number;
  registrationNo: string;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// Response types — Flight module
// Source: com.thanh.backend.flight.dto.response.*
//
// NOTE: FlightResponse.java does NOT include `availableSeats` or `duration`.
// Those appear in api-plan.md as planned search response additions,
// but the Java DTO class has not been updated yet.
// TODO: Once backend adds these fields, extend this type accordingly.
// ---------------------------------------------------------------------------

export interface FlightResponse {
  id: number;
  flightNo: string;
  airline: AirlineResponse;
  aircraft: AircraftResponse;
  origin: AirportResponse;
  dest: AirportResponse;
  departureTime: string; // OffsetDateTime -> ISO-8601
  arrivalTime: string;   // OffsetDateTime -> ISO-8601
  status: FlightStatus;
  basePrice: number;     // BigDecimal -> number
  source: FlightSource;
  createdAt: string;
  updatedAt: string;
}

export interface FlightSeatResponse {
  id: number;
  seatNo: string;      // e.g. "12A"
  cabinClass: CabinClass;
  price: number;       // BigDecimal -> number
  status: SeatStatus;
}

export interface FlightSeatMapResponse {
  flightId: number;
  flightNo: string;
  aircraft: AircraftResponse;
  seats: FlightSeatResponse[];
}

// ---------------------------------------------------------------------------
// Response types — Booking module
// Source: com.thanh.backend.booking.dto.response.*
// ---------------------------------------------------------------------------

export interface AddOnResponse {
  id: number;
  serviceType: AddOnType;
  description: string;
  price: number;    // BigDecimal -> number
  quantity: number;
}

export interface PassengerResponse {
  id: number;
  fullName: string;
  dateOfBirth: string; // LocalDate -> "YYYY-MM-DD"
  gender: Gender;
  nationality?: string;
  guardianId?: number;
  identityType?: DocumentType;
  identityNumber?: string;
  identityExpiry?: string;
}

export interface TicketResponse {
  id: number;
  ticketNo?: string;
  passengerType: PassengerType;
  price: number;
  status: TicketStatus;
  issuedAt?: string;
  flight: FlightResponse;
  seat: FlightSeatResponse | null;
  passengerId?: number;
  passengerName?: string;
  passenger?: PassengerResponse;
  addOns: AddOnResponse[];
}

export interface BookingResponse {
  paymentProvider?: string;
  cancellationReason?: 'PAYMENT_TIMEOUT' | 'USER_CANCELLED' | 'OTHER' | null;
  id: number;
  pnr: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  tripType: TripType;
  baseAmount: number;    // BigDecimal -> number
  taxAmount: number;
  discountAmount: number;
  totalAmount: number;
  status: BookingStatus;
  paymentDeadline: string; // OffsetDateTime -> ISO-8601
  createdAt: string;
  passengers: PassengerResponse[];
  tickets: TicketResponse[];
}

export interface HoldSeatsResponse {
  heldSeats: number[];   // list of seat IDs
  expiresAt: string;     // OffsetDateTime -> ISO-8601 — use this for the countdown, not a frontend timer
  ttlSeconds: number;
}

// ---------------------------------------------------------------------------
// Response types — Payment module
// Source: com.thanh.backend.payment.dto.response.*
//
// PaymentResponse.qrCode is the payOS VietQR/EMV payload; encode locally.
// ---------------------------------------------------------------------------

export interface PaymentResponse {
  id: number;
  bookingId: number;
  orderCode?: number;
  provider: string;
  providerTxnId?: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  refundStatus: RefundStatus;
  paymentUrl?: string;
  /** Raw payOS VietQR/EMV payload. Never rebuild bank details from client constants. */
  qrCode?: string;
  bankBin?: string | null;
  accountNumber?: string | null;
  accountName?: string | null;
  transferDescription?: string | null;
  transferAmount?: number | null;
  expiredAt?: string;  // OffsetDateTime -> ISO-8601
  paidAt?: string;
  createdAt: string;
}

export interface PaymentStatusResponse {
  bookingId: number;
  bookingStatus: BookingStatus;
  paymentStatus: PaymentStatus | "NOT_CREATED";
  payment: PaymentResponse | null;
}

// ---------------------------------------------------------------------------
// Request types — Auth
// ---------------------------------------------------------------------------

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone: string;
}

// ---------------------------------------------------------------------------
// Request types — Booking
// Source: com.thanh.backend.booking.dto.request.*
// ---------------------------------------------------------------------------

export interface HoldSeatsRequest {
  seatIds: number[];
}

export interface ContactInfoRequest {
  fullName: string;
  email: string;
  phone: string;
}

export interface AddOnRequest {
  serviceType: AddOnType;
  quantity?: number;
}

export interface PassengerRequest {
  fullName: string;
  dateOfBirth: string;      // "YYYY-MM-DD"
  gender: Gender;
  nationality?: string;
  passengerType: PassengerType;
  guardianPassengerIndex?: number;
  identityType?: DocumentType;
  identityNumber?: string;
  identityExpiry?: string;  // "YYYY-MM-DD"
}

export interface SeatSelectionRequest {
  flightSeatId: number;
  flightId: number;
  passengerIndex: number;
  addOns?: AddOnRequest[];
}

export interface CreateBookingRequest {
  tripType: TripType;
  flightIds: number[];
  contactInfo: ContactInfoRequest;
  passengers: PassengerRequest[];
  seatSelections: SeatSelectionRequest[];
  voucherId?: number;
}

// ---------------------------------------------------------------------------
// Request types — Payment
// Source: com.thanh.backend.payment.dto.request.*
// ---------------------------------------------------------------------------

export interface CreatePaymentLinkRequest {
  bookingId: number;
}

// ---------------------------------------------------------------------------
// Voucher
// Source: api-plan.md Module 9 — not yet implemented in backend Java source
// TODO: Reconcile once VoucherResponse.java is created.
// ---------------------------------------------------------------------------

export interface VoucherValidateRequest {
  code: string;
  bookingAmount: number;
}

/** Temporary shape based on api-plan.md. Confirm against VoucherResponse.java when available. */
export interface VoucherResponse {
  voucherId: number;
  discountType: "PERCENT" | "FIXED"; // TODO: confirm enum from backend
  value: number;
  discountAmount: number;
  finalAmount: number;
}
