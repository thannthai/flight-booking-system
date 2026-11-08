import type { BookingSession, BookingStep } from '@/lib/checkout-model'
export function bookingStep(value: unknown): BookingStep {
  return ['home', 'results', 'passengers', 'seats', 'payment'].includes(String(value)) ? value as BookingStep : 'home'
}
const record = (x: unknown): x is Record<string, unknown> => !!x && typeof x === 'object' && !Array.isArray(x)
const finite = (x: unknown): x is number => typeof x === 'number' && Number.isFinite(x)
const integer = (x: unknown): x is number => typeof x === 'number' && Number.isSafeInteger(x)
export function readBookingSession(raw: string | null, ownerId: number | null): BookingSession | null {
  try {
    const data: unknown = JSON.parse(raw || 'null')
    if (!record(data) || data.version !== 3 || data.ownerId !== ownerId || !finite(data.updatedAt) || Date.now() - data.updatedAt > 86400000) return null
    const search = data.searchParams
    if (!record(search) || !record(search.passengers)
      || typeof search.originCode !== 'string' || !/^[A-Z]{3}$/.test(search.originCode)
      || typeof search.destinationCode !== 'string' || !/^[A-Z]{3}$/.test(search.destinationCode)
      || typeof search.departureDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(search.departureDate)) return null
    const { adults, children, infants } = search.passengers
    if (!integer(adults) || !integer(children) || !integer(infants) || adults < 1 || children < 0 || infants < 0 || adults + children > 9 || infants > adults
      || typeof search.cabinClass !== 'string' || !['ECONOMY','BUSINESS'].includes(search.cabinClass)) return null
    const flightValid = (f: unknown) => f === null || (record(f) && integer(f.id) && finite(f.basePrice)
      && typeof f.departureTime === 'string' && Number.isFinite(Date.parse(f.departureTime))
      && record(f.origin) && typeof f.origin.timezone === 'string' && record(f.dest) && typeof f.dest.timezone === 'string')
    if (!flightValid(data.selectedFlight) || !flightValid(data.selectedReturnFlight)) return null
    if (![data.selectedSeats, data.selectedReturnSeats].every(list => Array.isArray(list) && list.every(s => record(s) && integer(s.id) && finite(s.price)))) return null
    const count = adults + children + infants
    for (const draft of [data.passengerDraft, data.passengerData]) {
      if (draft === null) continue
      if (!record(draft) || !Array.isArray(draft.passengers) || draft.passengers.length !== count || !record(draft.contactInfo) || !record(draft.passengerAddOns)) return null
      const contact = draft.contactInfo
      if (!['name','email','phone'].every(key => typeof contact[key] === 'string')
        || !draft.passengers.every((p: unknown) => record(p) && ['title','name','dob','idType','id','idExpiry','nationality'].every(key => typeof p[key] === 'string'))) return null
    }
    const completed = data.passengerData
    if (completed !== null && (!record(completed) || !Array.isArray(completed.paxList) || completed.paxList.length !== count
      || !Array.isArray(completed.passengerRequests) || completed.passengerRequests.length !== count
      || !completed.paxList.every((p: unknown) => record(p) && typeof p.type === 'string' && ['Adult','Child','Infant'].includes(p.type)))) return null
    if (data.bookingData !== null && (!record(data.bookingData) || !integer(data.bookingData.id))) return null
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
