export type SeatStatus = "AVAILABLE" | "HELD" | "BOOKED"
export type CabinClass = "ECONOMY" | "BUSINESS"
export type FlightStatus = "SCHEDULED" | "DELAYED" | "CANCELLED" | "COMPLETED"
export type BookingStatus = "PENDING" | "COMPLETED" | "CANCELLED" | "PENDING_CANCEL" | "REFUNDED"
export type PaymentStatus = "PENDING" | "SUCCESS" | "CANCELLED" | "EXPIRED" | "PAID_AFTER_TERMINAL"
export type PassengerType = "ADULT" | "CHILD" | "INFANT"
export type TripType = "ONE_WAY" | "ROUND_TRIP"

export interface AirportResponse {
  id: number; iataCode: string; name: string; city: string; country: string;
  timezone?: string; latitude?: number; longitude?: number;
}
export interface AirlineResponse {
  id: number; code: string; name: string; logoUrl?: string;
  freeBaggage: boolean; extraBaggageFee: number;
}
export interface AircraftResponse {
  id: number; airline: AirlineResponse; model: string;
  totalSeats: number; businessSeats: number; economySeats: number;
  registrationNo: string;
}
export interface FlightResponse {
  id: number; flightNo: string; airline: AirlineResponse; aircraft: AircraftResponse;
  origin: AirportResponse; dest: AirportResponse;
  departureTime: string; arrivalTime: string;
  status: FlightStatus; basePrice: number;
  availableSeats?: number;
  duration?: number;
}
export interface FlightSeatResponse {
  id: number; seatNo: string; cabinClass: CabinClass;
  price: number; status: SeatStatus;
}
export interface FlightSeatMapResponse {
  flightId: number; flightNo: string; aircraft: AircraftResponse;
  seats: FlightSeatResponse[];
}
export interface HoldSeatsResponse {
  heldSeats: number[]; expiresAt: string; ttlSeconds: number;
}
export interface BookingResponse {
  id: number; pnr: string;
  contactName: string; contactEmail: string; contactPhone: string;
  tripType: TripType; baseAmount: number; taxAmount: number;
  discountAmount: number; totalAmount: number;
  status: BookingStatus; paymentDeadline: string; createdAt: string;
}
export interface PaymentResponse {
  id: number; bookingId: number; orderCode?: number;
  provider: string; providerTxnId?: string;
  amount: number; currency: string;
  status: PaymentStatus;
  paymentUrl?: string; qrCode?: string;
  expiredAt?: string; paidAt?: string;
}
