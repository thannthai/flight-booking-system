import type { PassengerType } from '@/types/api'
export interface PassengerAddOns { baggage: number; meal: boolean; insurance: boolean }
const zero = BigInt(0), hundred = BigInt(100)
function cents(value: number): bigint {
  if (!Number.isFinite(value) || value < 0 || value > Number.MAX_SAFE_INTEGER / 100) throw new Error('Invalid monetary amount')
  return BigInt(value.toFixed(2).replace('.', ''))
}
const rate = (value: bigint, percent: number) => (value * BigInt(percent) + BigInt(50)) / hundred
const amount = (value: bigint) => Number(value) / 100
export function addOnAmount(settings?: PassengerAddOns): number {
  return settings ? settings.baggage + (settings.meal ? 80000 : 0) + (settings.insurance ? 50000 : 0) : 0
}
// Mirrors BookingService: CHILD 75% per seat, INFANT 10% per leg; add-ons are in taxable base.
export function priceBooking(flightBasePrices: number[], passengers: PassengerType[], seats: { passengerIndex: number; price: number }[], addOns: Record<number, PassengerAddOns> = {}) {
  let fare = zero
  for (const seat of seats) fare += rate(cents(seat.price), passengers[seat.passengerIndex] === 'CHILD' ? 75 : 100)
  for (const passenger of passengers) if (passenger === 'INFANT') for (const base of flightBasePrices) fare += rate(cents(base), 10)
  let extras = zero
  for (const [index, settings] of Object.entries(addOns)) if (passengers[Number(index)] && passengers[Number(index)] !== 'INFANT') extras += cents(addOnAmount(settings))
  const base = fare + extras
  const tax = rate(base, 10)
  const total = base + tax
  return { fareAmount: amount(fare), addOnsTotal: amount(extras), baseAmount: amount(base), taxAmount: amount(tax), totalAmount: amount(total), payableVnd: Number((total + BigInt(50)) / hundred) }
}
