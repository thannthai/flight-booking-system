'use client'

import Image from 'next/image'

import { createPortal } from 'react-dom'
import { useState } from 'react'
import { X, Printer, Plane, ShieldCheck, Utensils, Luggage, CheckCircle2 } from 'lucide-react'
import { useDialog } from '@/lib/use-dialog'
import { Button } from '@/components/ui/button'
import type { BookingResponse } from '@/types/api'
import { formatTime, formatDate } from '@/lib/format'

interface BoardingPassModalProps {
  isOpen: boolean
  onClose: () => void
  booking: BookingResponse | null
}

export function BoardingPassModal({ isOpen, onClose, booking }: BoardingPassModalProps) {
  const dialogRef = useDialog(isOpen, onClose)
  const [selectedTicketIndex, setSelectedTicketIndex] = useState<number | 'all'>('all')
  if (!isOpen || !booking || typeof document === 'undefined') return null

  const tickets = booking.tickets || []
  const demoPayment = booking.paymentProvider === 'DEMO'
  const title = demoPayment ? 'Demo E-Tickets' : 'E-Tickets'
  const displayedTickets = selectedTicketIndex === 'all' ? tickets : [tickets[selectedTicketIndex]].filter(Boolean)
  const disclaimer = `${demoPayment ? 'Demo booking confirmation only.' : 'Booking confirmation only.'} This is not an airline boarding pass or a document for airport security.`

  // A body-level portal keeps print layout independent of page/modal ancestors.
  return createPortal(
    <div data-ticket-print-root ref={dialogRef} role="dialog" aria-modal="true" aria-label={title} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-3 backdrop-blur-sm sm:p-6">
      <style>{`
        @media print {
          @page { size: A4; margin: 12mm; }
          html, body { height: auto !important; overflow: visible !important; background: white !important; }
          body:has([data-ticket-print-root]) > :not([data-ticket-print-root]) { display: none !important; }
          [data-ticket-print-root] { position: static !important; display: block !important; padding: 0 !important; overflow: visible !important; background: white !important; backdrop-filter: none !important; }
          [data-ticket-print-root] * { print-color-adjust: exact; -webkit-print-color-adjust: exact; }
          [data-ticket-print-root] .no-print { display: none !important; }
          [data-ticket-print-root] .print-only { display: block !important; }
          .ticket-modal { display: block !important; width: 100% !important; max-width: none !important; max-height: none !important; height: auto !important; overflow: visible !important; border: 0 !important; border-radius: 0 !important; box-shadow: none !important; background: white !important; }
          .ticket-list { display: block !important; overflow: visible !important; padding: 0 !important; background: white !important; }
          .ticket-card { margin: 0 !important; break-inside: avoid-page; box-shadow: none !important; border-radius: 8px !important; }
          .ticket-card + .ticket-card { break-before: page; }
          .ticket-header { background: #f1f5f9 !important; }
          .ticket-header * { color: #102a56 !important; }
          .ticket-body { display: grid !important; grid-template-columns: minmax(0, 1fr) 170px !important; }
          .ticket-main { padding: 20px !important; }
          .ticket-summary { padding: 18px !important; border-left: 1px dashed #cbd5e1 !important; border-top: 0 !important; }
          .ticket-summary dl { grid-template-columns: 1fr !important; gap: 12px !important; }
          .ticket-route { padding: 20px 0 !important; }
        }
      `}</style>
      <div className="ticket-modal flex max-h-[92vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-[#f7f8fa] shadow-2xl">
        <div className="no-print flex shrink-0 items-center justify-between gap-4 border-b border-slate-200 bg-white px-5 py-4 sm:px-7">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2"><h2 className="text-lg font-bold text-[#102a56]">{title}</h2><span className="rounded-full border border-slate-200 px-2 py-0.5 text-[10px] font-bold text-slate-600">{booking.status}</span></div>
            <p className="mt-1 text-xs text-slate-500">PNR <strong className="font-mono text-[#102a56]">{booking.pnr}</strong> · {booking.passengers?.length || 0} passenger(s)</p>
          </div>
          <button type="button" aria-label="Close tickets" onClick={onClose} className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-xl text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X className="size-5" /></button>
        </div>
        {demoPayment && <p className="no-print shrink-0 border-b border-amber-100 bg-amber-50 px-5 py-3 text-xs text-amber-900 sm:px-7">Simulated payment — no funds transferred. Demo booking confirmation only.</p>}
        {tickets.length > 1 && <div className="no-print flex shrink-0 gap-2 overflow-x-auto border-b border-slate-200 px-5 py-3 sm:px-7">
          <button type="button" onClick={() => setSelectedTicketIndex('all')} className={`shrink-0 cursor-pointer rounded-lg px-3 py-2 text-xs font-semibold ${selectedTicketIndex === 'all' ? 'bg-[#102a56] text-white' : 'bg-white text-slate-600'}`}>All E-Tickets ({tickets.length})</button>
          {tickets.map((ticket, index) => <button key={ticket.id} type="button" onClick={() => setSelectedTicketIndex(index)} className={`shrink-0 cursor-pointer rounded-lg px-3 py-2 text-xs font-semibold ${selectedTicketIndex === index ? 'bg-[#102a56] text-white' : 'bg-white text-slate-600'}`}>{ticket.passengerName || ticket.passenger?.fullName || `Passenger ${index + 1}`} · {ticket.flight?.flightNo} · {ticket.passengerType === 'INFANT' ? 'Lap infant' : ticket.seat?.seatNo || 'No seat'}</button>)}
        </div>}
        <div id="boarding-pass-print-area" className="ticket-list min-h-0 flex-1 space-y-6 overflow-y-auto p-4 sm:p-7">
          {displayedTickets.length === 0 && <p className="py-12 text-center text-slate-500">No ticket details available for this booking.</p>}
          {displayedTickets.map(ticket => {
            const flight = ticket.flight
            if (!flight) return <p key={ticket.id}>Flight details unavailable. Refresh booking details.</p>
            const passengerName = (ticket.passengerName || ticket.passenger?.fullName || booking.contactName || 'Passenger').toUpperCase()
            const infant = ticket.passengerType === 'INFANT'
            const seat = infant ? 'No separate seat' : ticket.seat?.seatNo || 'Not provided'
            const cabin = infant ? 'Lap infant' : ticket.seat?.cabinClass || 'Not provided'
            const depDate = formatDate(flight.departureTime, 'en-GB', flight.origin.timezone)
            const arrDate = formatDate(flight.arrivalTime, 'en-GB', flight.dest.timezone)
            return <article key={ticket.id} className="ticket-card overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
              <div className="ticket-header flex flex-wrap items-center justify-between gap-3 border-b-2 border-[#f59e0b] bg-[#102a56] px-5 py-4 text-white sm:px-6">
                <div className="flex min-w-0 items-center gap-3"><span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-white p-1"><Image unoptimized width={32} height={32} src={`/airlines/${flight.airline.code}.svg`} alt={flight.airline.name} className="h-full w-full object-contain" onError={event => { event.currentTarget.style.display = 'none' }} /></span><div className="min-w-0"><h3 className="text-sm font-bold sm:text-base">{flight.airline.name}</h3><p className="mt-1 text-xs text-slate-300">{flight.flightNo} · {flight.aircraft?.model || 'Aircraft not provided'}</p></div></div>
                <span className="text-xs font-semibold tracking-wider">{demoPayment ? 'DEMO E-TICKET' : 'E-TICKET'}</span>
              </div>
              <div className="ticket-body grid lg:grid-cols-[minmax(0,1fr)_240px]">
                <div className="ticket-main min-w-0 p-5 sm:p-7">
                  <p className="text-[10px] font-semibold uppercase tracking-widest text-slate-500">Passenger</p>
                  <p className="mt-1 break-words text-xl font-bold text-[#102a56] sm:text-2xl">{passengerName}</p>
                  <p className="mt-1 text-xs text-slate-500">{ticket.passengerType}{infant ? ' · Accompanied by an adult' : ''}</p>
                  <div className="ticket-route grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2 border-b border-slate-100 py-6 sm:gap-5">
                    <div><p className="text-xs text-slate-500">From</p><p className="mt-1 text-3xl font-bold text-[#102a56] sm:text-4xl">{flight.origin.iataCode}</p><p className="mt-1 text-xs text-slate-600">{flight.origin.city}</p><p className="mt-3 text-lg font-semibold">{formatTime(flight.departureTime, flight.origin.timezone)}</p><p className="text-xs text-slate-500">{depDate}</p></div>
                    <Plane aria-hidden="true" className="size-5 rotate-45 text-amber-500" />
                    <div className="text-right"><p className="text-xs text-slate-500">To</p><p className="mt-1 text-3xl font-bold text-[#102a56] sm:text-4xl">{flight.dest.iataCode}</p><p className="mt-1 text-xs text-slate-600">{flight.dest.city}</p><p className="mt-3 text-lg font-semibold">{formatTime(flight.arrivalTime, flight.dest.timezone)}</p><p className="text-xs text-slate-500">{arrDate}</p></div>
                  </div>
                  <p className="mt-4 text-xs leading-relaxed text-slate-500">Times are local to each airport. Boarding time and gate are not provided; check with the airline.</p>
                  {flight.source === 'DEMO' && <p className="mt-2 text-xs font-medium text-amber-800">Simulated schedule</p>}
                  {!!ticket.addOns?.length && <div className="mt-4 flex flex-wrap gap-2">{ticket.addOns.map((addOn, index) => <span key={addOn.id || index} className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600">{addOn.serviceType === 'BAGGAGE' && <Luggage className="size-3" />}{addOn.serviceType === 'MEAL' && <Utensils className="size-3" />}{addOn.serviceType === 'INSURANCE' && <ShieldCheck className="size-3" />}{addOn.description || addOn.serviceType}</span>)}</div>}
                </div>
                <aside className="ticket-summary border-t border-dashed border-slate-200 bg-slate-50 p-5 lg:border-t-0 lg:border-l sm:p-6">
                  <p className="text-[10px] font-semibold uppercase tracking-widest text-slate-500">Booking reference</p><p className="mt-1 font-mono text-2xl font-bold tracking-wider text-[#102a56]">{booking.pnr}</p>
                  <dl className="mt-5 grid grid-cols-2 gap-4 border-y border-slate-200 py-4">
                    <div><dt className="text-xs text-slate-500">Seat</dt><dd className={`${infant ? 'text-sm' : 'text-2xl'} mt-1 font-bold text-[#102a56]`}>{seat}</dd></div>
                    <div><dt className="text-xs text-slate-500">Cabin</dt><dd className="mt-1 break-words text-xs font-semibold text-slate-800">{cabin}</dd></div>
                  </dl>
                  <p className="mt-4 text-xs text-slate-500">Ticket status</p><p className={`mt-1 flex items-center gap-1.5 text-sm font-semibold ${ticket.status === 'ISSUED' ? 'text-emerald-700' : 'text-slate-700'}`}>{ticket.status === 'ISSUED' && <CheckCircle2 className="size-4" />}{ticket.status}</p>
                  <p className="mt-5 text-xs text-slate-500">Ticket number</p><p className="mt-1 break-all font-mono text-xs font-semibold text-[#102a56]">{ticket.ticketNo || 'Not issued'}</p>
                </aside>
              </div>
              <p className="print-only hidden border-t border-slate-200 px-5 py-3 text-[10px] text-slate-600">{demoPayment && 'Simulated payment — no funds transferred. '}{disclaimer}</p>
            </article>
          })}
        </div>
        <div className="no-print flex shrink-0 flex-col items-center justify-between gap-4 border-t border-slate-200 bg-white px-5 py-4 sm:flex-row sm:px-7">
          <p className="max-w-2xl text-center text-xs leading-relaxed text-slate-500 sm:text-left">{disclaimer}</p>
          <div className="flex w-full shrink-0 gap-3 sm:w-auto"><Button type="button" variant="outline" onClick={onClose} className="flex-1 sm:flex-initial">Close</Button><Button type="button" disabled={!displayedTickets.length} onClick={() => window.print()} className="flex-1 gap-2 bg-[#102a56] text-white hover:bg-slate-800 sm:flex-initial"><Printer className="size-4" />{displayedTickets.length > 1 ? 'Print E-Tickets' : 'Print E-Ticket'}</Button></div>
        </div>
      </div>
    </div>, document.body,
  )
}
