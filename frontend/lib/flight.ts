import type { FlightResponse } from '@/types/api'
import { computeDuration, formatTime, formatDate, formatVND } from '@/lib/format'
export interface AdaptedFlight {
  id: number; airline: string; code: string; logo: string; tone: string
  depart: string; arrive: string; duration: string; price: string; aircraft: string; badge: string; departDate: string
  basePrice: number; departureTime: string; arrivalTime: string
  origin: FlightResponse['origin']; dest: FlightResponse['dest']; source: FlightResponse['source']
}
export function adaptFlight(flight: FlightResponse): AdaptedFlight {
  return { id: flight.id, airline: flight.airline.name, code: flight.flightNo, logo: flight.airline.code,
    tone: flight.airline.code === 'VN' ? 'bg-sky-900' : flight.airline.code === 'VJ' ? 'bg-red-600' : 'bg-emerald-700',
    depart: formatTime(flight.departureTime, flight.origin.timezone), arrive: formatTime(flight.arrivalTime, flight.dest.timezone),
    departDate: formatDate(flight.departureTime, 'en-GB', flight.origin.timezone),
    duration: computeDuration(flight.departureTime, flight.arrivalTime), price: formatVND(flight.basePrice), aircraft: flight.aircraft.model, badge: '',
    basePrice: flight.basePrice, departureTime: flight.departureTime, arrivalTime: flight.arrivalTime, origin: flight.origin, dest: flight.dest, source: flight.source }
}
