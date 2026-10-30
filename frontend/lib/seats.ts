import type { CabinClass, FlightSeatResponse } from '@/types/api'
export function assignSeats(seats: FlightSeatResponse[], current: (FlightSeatResponse | null)[], cabin: CabinClass): (FlightSeatResponse | null)[] {
  const assigned = new Set(current.filter(Boolean).map(s => s!.id))
  const available = seats.filter(s => s.status === 'AVAILABLE' && s.cabinClass === cabin && !assigned.has(s.id)).sort((a,b) => a.price - b.price || a.seatNo.localeCompare(b.seatNo, undefined, {numeric:true}))
  return current.map(s => s || available.shift() || null)
}
