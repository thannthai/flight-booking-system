import type { PassengerDraft, PassengerSlot } from '@/lib/checkout-model'

// Fictional, per-draft identifiers prevent collisions across shared demo-account bookings.
export function demoPassengerDraft(slots: PassengerSlot[], date: string, identity = crypto.randomUUID().replaceAll('-', '').toUpperCase()): PassengerDraft {
  const year = Number(date.slice(0, 4))
  const adults = slots.flatMap((slot, index) => slot.type === 'Adult' ? [index] : [])
  let infant = 0
  const passengers = slots.map((slot, index) => ({
    title: slot.type === 'Adult' ? 'Mr' : 'Mstr',
    name: `Demo ${slot.type} ${index + 1}`,
    dob: `${year - (slot.type === 'Adult' ? 30 : slot.type === 'Child' ? 8 : 1)}-01-01`,
    idType: 'PASSPORT' as const,
    id: `DEMO${identity.slice(0, 20)}${index}`,
    idExpiry: `${year + 10}-01-01`, nationality: 'Vietnam',
    guardianIndex: slot.type === 'Infant' ? adults[infant++] : null,
  }))
  passengers.forEach((p, i) => {
    if (slots[i].type === 'Infant' && p.guardianIndex != null) {
      p.id = passengers[p.guardianIndex].id
      p.idExpiry = passengers[p.guardianIndex].idExpiry
    }
  })
  return {contactInfo:{name:'Demo Traveler',email:'traveler@demo.invalid',phone:'0900000000'},passengers,passengerAddOns:{}}
}
