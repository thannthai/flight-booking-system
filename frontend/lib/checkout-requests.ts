import type { AddOnRequest, CreateBookingRequest, FlightSeatResponse, PassengerRequest } from '@/types/api'
import { passengerType, type CheckoutData, type PassengerForm, type PassengerSlot } from '@/lib/checkout-model'
import { documentFields } from '@/lib/passenger-validation'

export function buildPassengerRequests(slots: PassengerSlot[], forms: PassengerForm[]): PassengerRequest[] {
  return slots.map((slot, index) => {
    const form = forms[index]
    if (!form) throw new Error('Passenger information is incomplete.')
    const guardian = form.guardianIndex
    if (slot.type === 'Infant' && (guardian == null || !Number.isInteger(guardian) || slots[guardian]?.type !== 'Adult')) throw new Error('Select a valid adult guardian.')
    return {
      fullName: form.name.trim(), dateOfBirth: form.dob,
      gender: form.title === 'Ms' || form.title === 'Miss' ? 'FEMALE' : 'MALE',
      passengerType: passengerType(slot.type), ...documentFields(form),
      nationality: form.nationality || 'Vietnam',
      guardianPassengerIndex: slot.type === 'Infant' ? guardian ?? undefined : undefined,
    }
  })
}

interface BookingInput {
  flight: { id: number }
  returnFlight?: { id: number } | null
  seats: (FlightSeatResponse | null)[]
  returnSeats: (FlightSeatResponse | null)[]
  passengerData: CheckoutData
}
export function buildBookingRequest({ flight, returnFlight, seats, returnSeats, passengerData }: BookingInput): CreateBookingRequest {
  const eligible = passengerData.paxList.map((p, index) => ({ ...p, index })).filter(p => p.type !== 'Infant')
  const selections = (flightId: number, chosen: (FlightSeatResponse | null)[], outbound: boolean) => eligible.map((p, index) => {
    const seat = chosen[index]
    if (!seat) throw new Error('Seat selection is incomplete. Return to seats.')
    const settings = outbound ? passengerData.passengerAddOns?.[p.index] : undefined
    const addOns: AddOnRequest[] = []
    if (settings?.baggage) addOns.push({ serviceType: 'BAGGAGE', quantity: Math.round(settings.baggage / 200000) })
    if (settings?.meal) addOns.push({ serviceType: 'MEAL', quantity: 1 })
    if (settings?.insurance) addOns.push({ serviceType: 'INSURANCE', quantity: 1 })
    return { flightId, flightSeatId: seat.id, passengerIndex: p.index, addOns }
  })
  return {
    tripType: returnFlight ? 'ROUND_TRIP' : 'ONE_WAY',
    flightIds: returnFlight ? [flight.id, returnFlight.id] : [flight.id],
    contactInfo: { fullName: passengerData.contactInfo.name, email: passengerData.contactInfo.email, phone: passengerData.contactInfo.phone },
    passengers: passengerData.passengerRequests,
    seatSelections: [...selections(flight.id, seats, true), ...(returnFlight ? selections(returnFlight.id, returnSeats, false) : [])],
  }
}
