import { bookingStatusLabel } from '@/lib/booking-status'
import type { BookingResponse, FlightSeatResponse } from '@/types/api'
import type { AdaptedFlight } from '@/lib/flight'
import type { CheckoutData } from '@/lib/checkout-model'
import { adaptFlight } from '@/lib/flight'
import { formatDate, formatTime } from '@/lib/format'

type Leg = { id: number } & Partial<AdaptedFlight>
export function TripSummary({ booking, flight, returnFlight, seats = [], returnSeats = [], passengerData }: {
  booking?: BookingResponse; flight?: Leg | null; returnFlight?: Leg | null
  seats?: (FlightSeatResponse | null)[]; returnSeats?: (FlightSeatResponse | null)[]; passengerData?: CheckoutData | null
}) {
  const legs: Leg[] = booking
    ? Array.from(new Map((booking.tickets || []).map(t => [t.flight.id, adaptFlight(t.flight)])).values()).sort((a, b) => Date.parse(a.departureTime) - Date.parse(b.departureTime))
    : [flight, returnFlight].filter((leg): leg is Leg => !!leg)
  return <div className="space-y-5">
    {booking && <p className="text-sm text-slate-500">Booking reference <strong className="text-[#102a56]">{booking.pnr}</strong> · {bookingStatusLabel(booking)}</p>}
    {legs.map((leg, index) => {
      let seated = 0
      const travelers = booking ? (booking.tickets || []).filter(t => t.flight.id === leg.id).map(t => ({
        name: t.passengerName || t.passenger?.fullName || booking.passengers?.find(p => p.id === t.passengerId)?.fullName || 'Passenger',
        infant: t.passengerType === 'INFANT', seat: t.seat,
      })) : (passengerData?.paxList || []).map((p, i) => ({
        name: passengerData?.passengerRequests[i]?.fullName || p.title || `Passenger ${i + 1}`,
        infant: p.type === 'Infant', seat: p.type === 'Infant' ? null : (index === 0 ? seats : returnSeats)[seated++],
      }))
      return <section key={leg.id} className="rounded-xl border border-slate-200 bg-white p-4">
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{index === 0 ? 'Outbound' : 'Return'}</p>
        <h3 className="mt-2 text-lg font-semibold text-[#102a56]">{leg.origin?.iataCode} → {leg.dest?.iataCode}</h3>
        <p className="mt-1 text-sm text-slate-600">{leg.airline} · {leg.code || `Flight #${leg.id}`}</p>
        <div className="mt-3 grid grid-cols-2 gap-4 text-sm">
          <div><p className="text-xs text-slate-500">Departure · {leg.origin?.iataCode}</p><p className="font-semibold">{leg.departureTime ? formatTime(leg.departureTime, leg.origin?.timezone) : leg.depart || '—'}</p><p>{leg.departureTime ? formatDate(leg.departureTime, 'en-GB', leg.origin?.timezone) : leg.departDate}</p></div>
          <div><p className="text-xs text-slate-500">Arrival · {leg.dest?.iataCode}</p><p className="font-semibold">{leg.arrivalTime ? formatTime(leg.arrivalTime, leg.dest?.timezone) : leg.arrive || '—'}</p><p>{leg.arrivalTime ? formatDate(leg.arrivalTime, 'en-GB', leg.dest?.timezone) : '—'}</p></div>
        </div>
        {leg.source === 'DEMO' && <p className="mt-3 text-xs text-amber-800">Simulated schedule</p>}
        <ul className="mt-4 space-y-2 border-t border-slate-100 pt-3 text-sm">{travelers.map((p, i) => <li key={i} className="flex flex-wrap justify-between gap-2"><span>{p.name}</span><span className="font-medium text-[#102a56]">{p.infant ? 'Infant · No separate seat' : `${p.seat?.seatNo || '—'} · ${p.seat?.cabinClass || '—'}`}</span></li>)}</ul>
      </section>
    })}
  </div>
}
