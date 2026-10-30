import type { BookingSession, BookingStep } from '@/lib/checkout-model'
export function bookingStep(value: unknown): BookingStep {
  return ['home', 'results', 'passengers', 'seats', 'payment'].includes(String(value)) ? value as BookingStep : 'home'
}
const record = (x: unknown): x is Record<string, any> => !!x && typeof x === 'object'
export function readBookingSession(raw: string | null, ownerId: number | null): BookingSession | null {
  try {
    const data: unknown = JSON.parse(raw || 'null')
    if (!record(data) || data.version !== 3 || data.ownerId !== ownerId || !Number.isFinite(data.updatedAt) || Date.now() - data.updatedAt > 86400000) return null
    const search = data.searchParams
    if (!record(search) || !record(search.passengers) || !/^[A-Z]{3}$/.test(search.originCode) || !/^[A-Z]{3}$/.test(search.destinationCode) || !/^\d{4}-\d{2}-\d{2}$/.test(search.departureDate)) return null
    const { adults, children, infants } = search.passengers
    if (![adults,children,infants].every(n => Number.isInteger(n) && n >= 0) || adults < 1 || adults + children > 9 || infants > adults || !['ECONOMY','BUSINESS'].includes(search.cabinClass)) return null
    const flightValid = (f: unknown) => f === null || (record(f) && Number.isSafeInteger(f.id) && Number.isFinite(f.basePrice) && Number.isFinite(Date.parse(f.departureTime)) && record(f.origin) && typeof f.origin.timezone === 'string' && record(f.dest) && typeof f.dest.timezone === 'string')
    if (!flightValid(data.selectedFlight) || !flightValid(data.selectedReturnFlight)) return null
    if (![data.selectedSeats, data.selectedReturnSeats].every(list => Array.isArray(list) && list.every(s => record(s) && Number.isSafeInteger(s.id) && Number.isFinite(s.price)))) return null
    const count = adults + children + infants
    for (const draft of [data.passengerDraft, data.passengerData]) if (draft !== null && (!record(draft) || !Array.isArray(draft.passengers) || draft.passengers.length !== count || !record(draft.contactInfo) || !record(draft.passengerAddOns))) return null
    for (const draft of [data.passengerDraft, data.passengerData]) if (draft && (!['name','email','phone'].every(key => typeof draft.contactInfo[key] === 'string') || !draft.passengers.every((p: unknown) => record(p) && ['title','name','dob','idType','id','idExpiry','nationality'].every(key => typeof p[key] === 'string')))) return null
    if (data.passengerData && (!Array.isArray(data.passengerData.paxList) || data.passengerData.paxList.length !== count || !Array.isArray(data.passengerData.passengerRequests) || data.passengerData.passengerRequests.length !== count)) return null
    if (data.bookingData !== null && (!record(data.bookingData) || !Number.isSafeInteger(data.bookingData.id))) return null
    if (data.passengerData && !data.passengerData.paxList.every((p: unknown) => record(p) && ['Adult','Child','Infant'].includes(p.type))) return null
    return { ...data, view: bookingStep(data.view) } as BookingSession
  } catch { return null }
}
export function reachableStep(step: BookingStep, draft: Pick<BookingSession, 'selectedFlight' | 'passengerData' | 'selectedSeats' | 'selectedReturnFlight' | 'selectedReturnSeats' | 'bookingData' | 'holdExpiresAt'>, recoveryId?: number | null): BookingStep {
  if (step === 'home' || step === 'results') return step
  if (step === 'payment' && (draft.bookingData?.id || recoveryId)) return 'payment'
  if (!draft.selectedFlight) return 'results'
  if (step === 'passengers' || !draft.passengerData) return 'passengers'
  if (step === 'seats') return 'seats'
  const count = draft.passengerData.paxList.filter(p => p.type !== 'Infant').length
  return count > 0 && draft.selectedSeats.length === count && (!draft.selectedReturnFlight || draft.selectedReturnSeats.length === count) && !!draft.holdExpiresAt && Date.parse(draft.holdExpiresAt) > Date.now() ? 'payment' : 'seats'
}
