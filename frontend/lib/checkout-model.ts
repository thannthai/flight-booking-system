import type { BookingResponse, CabinClass, DocumentType, FlightSeatResponse, PassengerRequest, PassengerType } from '@/types/api'
import type { AdaptedFlight } from '@/lib/flight'
import type { PassengerAddOns } from '@/lib/pricing'

export interface SearchParams {
  originCode: string
  destinationCode: string
  departureDate: string
  returnDate: string | null
  passengers: { adults: number; children: number; infants: number }
  originTimezone?: string
  destinationTimezone?: string
  cabinClass: CabinClass
}
export type BookingStep = 'home' | 'results' | 'passengers' | 'seats' | 'payment'
export type PassengerCategory = 'Adult' | 'Child' | 'Infant'
export interface PassengerSlot { type: PassengerCategory; title: string }
export interface ContactForm { name: string; email: string; phone: string }
export interface PassengerForm {
  title: string; name: string; dob: string; idType: DocumentType | ''; id: string
  idExpiry: string; nationality: string; guardianIndex?: number | null
}
export interface PassengerDraft {
  contactInfo: ContactForm
  passengers: PassengerForm[]
  passengerAddOns: Record<number, PassengerAddOns>
}
export interface PassengerData extends PassengerDraft {
  paxList: PassengerSlot[]
  passengerRequests: PassengerRequest[]
  pricing?: { totalBaseFare: number; totalTaxes: number; addOnsTotal: number; discount: number }
}
export type CheckoutData = Pick<PassengerData, 'paxList' | 'passengerRequests' | 'contactInfo'> & Partial<Pick<PassengerData, 'passengerAddOns'>>
export interface BookingSession {
  version: 3; ownerId: number | null; updatedAt: number; view: BookingStep; searchParams: SearchParams
  selectedFlight: AdaptedFlight | null; selectedReturnFlight: AdaptedFlight | null
  passengerData: PassengerData | null; passengerDraft: PassengerDraft | null
  selectedSeats: FlightSeatResponse[]; selectedReturnSeats: FlightSeatResponse[]
  bookingData: BookingResponse | null; holdExpiresAt: string | null
}
export const passengerType = (category: PassengerCategory): PassengerType => ({ Adult: 'ADULT', Child: 'CHILD', Infant: 'INFANT' } as const)[category]

// HTML inputs and legacy stored drafts enter through this boundary, never through a cast.
export function guardianIndexInput(value: unknown): number | null {
  if (typeof value !== 'number' && (typeof value !== 'string' || !/^\d+$/.test(value))) return null
  const index = Number(value)
  return Number.isSafeInteger(index) && index >= 0 ? index : null
}
export function documentTypeInput(value: string): DocumentType | '' {
  return value === 'CITIZEN_ID' || value === 'PASSPORT' ? value : ''
}
