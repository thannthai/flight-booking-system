'use client'

import { useDialog } from '@/lib/use-dialog'
import { useState } from 'react'
import { X, Printer, Plane, ShieldCheck, Utensils, Luggage, CheckCircle2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { BookingResponse } from '@/types/api'
import { formatTime, formatDate } from '@/lib/format'

interface BoardingPassModalProps {
  isOpen: boolean
  onClose: () => void
  booking: BookingResponse | null
  flight?: any
}

export function BoardingPassModal({ isOpen, onClose, booking }: BoardingPassModalProps) {
  const dialogRef = useDialog(isOpen, onClose)
  const [selectedTicketIndex, setSelectedTicketIndex] = useState<number | 'all'>('all')

  if (!isOpen || !booking) return null

  const tickets = booking.tickets || []

  const handlePrint = () => {
    window.print()
  }

  const displayedTickets = selectedTicketIndex === 'all' 
    ? tickets 
    : [tickets[selectedTicketIndex]].filter(Boolean)

  return (
    <div ref={dialogRef} role="dialog" aria-modal="true" aria-label="Demo E-Tickets" className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-950/60 backdrop-blur-sm overflow-y-auto print:p-0 print:bg-white print:static">
      {/* Print Styles */}
      <style>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #boarding-pass-print-area, #boarding-pass-print-area * {
            visibility: visible;
          }
          #boarding-pass-print-area {
            position: absolute;
            left: 0;
            top: 0;
            width: 100%;
            margin: 0;
            padding: 0;
            background: white !important;
          }
          .no-print {
            display: none !important;
          }
          .print-break {
            page-break-after: always;
            break-after: page;
          }
        }
      `}</style>

      {/* Modal Container */}
      <div className="relative w-full max-w-4xl max-h-[92vh] flex flex-col bg-[#f7f8fa] rounded-3xl shadow-2xl border border-slate-200 overflow-hidden print:border-none print:shadow-none print:max-h-none print:rounded-none">
        
        {/* Modal Top Bar (Hidden in print) */}
        <div className="no-print flex items-center justify-between px-6 py-4 bg-white border-b border-slate-200">
          <div className="flex items-center gap-3">
            <span className="flex size-9 items-center justify-center rounded-xl bg-[#102a56] text-white">
              <Plane className="size-4.5 -rotate-12" />
            </span>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base font-bold text-slate-900">Demo E-Tickets</h2>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-100">
                  {booking.status}
                </span>
              </div>
              <p className="text-xs text-slate-500">
                PNR Reference: <span className="font-mono font-bold text-[#102a56]">{booking.pnr}</span> · {booking.passengers.length} {booking.passengers.length === 1 ? 'Passenger' : 'Passengers'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button
              type="button"
              onClick={handlePrint}
              className="h-9 px-4 rounded-xl bg-[#102a56] hover:bg-slate-800 text-white font-bold text-xs cursor-pointer flex items-center gap-2"
            >
              <Printer className="size-3.5" />
              <span>Print All Tickets</span>
            </Button>
            <button
              type="button"
              onClick={onClose}
              className="size-9 flex items-center justify-center rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
            >
              <X className="size-5" />
            </button>
          </div>
        </div>

        {/* Passenger Switcher Tabs (Hidden in print if all) */}
        {tickets.length > 1 && (
          <div className="no-print flex items-center gap-2 px-6 py-3 bg-slate-100/80 border-b border-slate-200 overflow-x-auto">
            <button
              type="button"
              onClick={() => setSelectedTicketIndex('all')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer ${
                selectedTicketIndex === 'all'
                  ? 'bg-[#102a56] text-white shadow-xs'
                  : 'bg-white text-slate-600 hover:bg-slate-200'
              }`}
            >
              All E-Tickets ({tickets.length})
            </button>
            {tickets.map((t, idx) => {
              const paxName = t.passengerName || t.passenger?.fullName || `Passenger ${idx + 1}`
              const isSelected = selectedTicketIndex === idx
              const seatLabel = t.passengerType === 'INFANT' ? 'Infant (Lap)' : t.seat?.seatNo ? `Seat ${t.seat.seatNo}` : 'No Seat'
              return (
                <button
                  key={t.id || idx}
                  type="button"
                  onClick={() => setSelectedTicketIndex(idx)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer ${
                    isSelected
                      ? 'bg-[#102a56] text-white shadow-xs'
                      : 'bg-white text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {paxName} ({seatLabel})
                </button>
              )
            })}
          </div>
        )}

        {/* Boarding Passes Printable Area */}
        <div id="boarding-pass-print-area" className="flex-1 overflow-y-auto p-4 sm:p-8 space-y-8">
          {displayedTickets.length === 0 ? (
            <div className="text-center py-12 text-slate-500">No ticket details available for this booking.</div>
          ) : (
            displayedTickets.map((ticket, idx) => {
              const ticketFlight = ticket.flight
              if (!ticketFlight) return <p key={ticket.id}>Flight details unavailable. Refresh booking details.</p>
              const airlineName = ticketFlight?.airline?.name || 'Not provided'
              const airlineCode = ticketFlight?.airline?.code || '—'
              const flightNo = ticketFlight?.flightNo || '—'
              const aircraft = ticketFlight?.aircraft?.model || 'Not provided'
              const originCode = ticketFlight?.origin?.iataCode || '—'
              const originCity = ticketFlight?.origin?.city || '—'
              const destCode = ticketFlight?.dest?.iataCode || '—'
              const destCity = ticketFlight?.dest?.city || '—'
              const depTime = ticketFlight?.departureTime ? formatTime(ticketFlight.departureTime, ticketFlight.origin?.timezone) : '—'
              const arrTime = ticketFlight?.arrivalTime ? formatTime(ticketFlight.arrivalTime, ticketFlight.dest?.timezone) : '—'
              const flightDate = ticketFlight?.departureTime ? formatDate(ticketFlight.departureTime, 'en-GB', ticketFlight.origin?.timezone) : '—'
              const boardingTime = 'Not provided'
              const passengerName = (ticket.passengerName || ticket.passenger?.fullName || booking.contactName || 'PASSENGER').toUpperCase()
              const isInfant = ticket.passengerType === 'INFANT'
              const seatNo = isInfant ? 'INFANT (LAP)' : ticket.seat?.seatNo || 'ASSIGNED'
              const cabinClass = isInfant ? 'LAP INFANT' : ticket.seat?.cabinClass || 'ECONOMY'
              const ticketNo = ticket.ticketNo || 'Not issued'

              return (
                <div 
                  key={ticket.id || idx} 
                  className="bg-white rounded-3xl shadow-md border border-slate-200/90 overflow-hidden print-break"
                >
                  {/* Top Header Ribbon */}
                  <div className="bg-[#102a56] text-white px-6 py-4 flex flex-wrap items-center justify-between gap-4 border-b-2 border-[#f59e0b]">
                    <div className="flex items-center gap-3">
                      <div className="size-9 bg-white rounded-lg p-1 flex items-center justify-center">
                        <img 
                          src={`/airlines/${airlineCode}.svg`} 
                          alt={airlineName} 
                          className="h-full w-full object-contain"
                          onError={(e) => {
                            (e.target as HTMLElement).style.display = 'none'
                          }} 
                        />
                      </div>
                      <div>
                        <p className="font-extrabold text-sm tracking-wide">{airlineName}</p>
                        <p className="text-xs text-slate-300 font-mono">{flightNo} · {aircraft}</p>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <span className="px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest bg-amber-400 text-slate-950">
                        {cabinClass}
                      </span>
                      <div className="text-right">
                        <span className="block text-[10px] uppercase font-bold tracking-wider text-slate-300">DEMO E-TICKET</span>
                        <span className="text-xs font-mono font-bold text-amber-300">{ticketNo}</span>
                      </div>
                    </div>
                  </div>

                  {/* Pass Main Grid: Left Main Section + Right Tear-off Stub */}
                  <div className="grid lg:grid-cols-[1fr_260px] relative">
                    
                    {/* Left: Main Ticket Body */}
                    <div className="p-6 sm:p-8 border-b lg:border-b-0 lg:border-r border-slate-200 relative">
                      
                      {/* Passenger Name & PNR Row */}
                      <div className="flex flex-wrap items-start justify-between gap-4 pb-6 border-b border-slate-100">
                        <div>
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest block">PASSENGER NAME</span>
                          <h3 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight mt-0.5">
                            {passengerName}
                          </h3>
                          <div className="flex items-center gap-2 mt-1">
                            <span className="text-[11px] font-semibold text-slate-500 uppercase">
                              Type: <strong className="text-slate-800">{ticket.passengerType}</strong>
                            </span>
                            {isInfant && (
                              <span className="text-[10px] font-bold bg-amber-100 text-amber-800 px-2 py-0.5 rounded-full">
                                Accompanied with Adult
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="text-right">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest block">BOOKING REF (PNR)</span>
                          <span className="text-2xl font-black font-mono tracking-wider text-[#102a56]">
                            {booking.pnr}
                          </span>
                        </div>
                      </div>

                      {/* Flight Route Details */}
                      <div className="py-6 border-b border-slate-100 grid grid-cols-[1fr_auto_1fr] items-center gap-4">
                        <div>
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">FROM</span>
                          <p className="text-3xl sm:text-4xl font-black text-[#102a56] tracking-tight">{originCode}</p>
                          <p className="text-xs font-semibold text-slate-700 mt-0.5">{originCity}</p>
                          <p className="text-sm font-bold text-slate-900 mt-1">{depTime}</p>
                        </div>

                        <div className="flex flex-col items-center justify-center px-2">
                          <span className="text-[10px] font-bold text-slate-400 mb-1">NON-STOP</span>
                          <div className="flex items-center gap-1.5 text-slate-300">
                            <span className="h-0.5 w-8 sm:w-16 bg-slate-200" />
                            <Plane className="size-5 text-[#f59e0b] -rotate-12 shrink-0" />
                            <span className="h-0.5 w-8 sm:w-16 bg-slate-200" />
                          </div>
                          <span className="text-[11px] font-medium text-slate-500 mt-1">{flightDate}</span>
                        </div>

                        <div className="text-right">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">TO</span>
                          <p className="text-3xl sm:text-4xl font-black text-[#102a56] tracking-tight">{destCode}</p>
                          <p className="text-xs font-semibold text-slate-700 mt-0.5">{destCity}</p>
                          <p className="text-sm font-bold text-slate-900 mt-1">{arrTime}</p>
                        </div>
                      </div>

                      {/* Boarding Info Grid */}
                      <div className="pt-6 grid grid-cols-2 sm:grid-cols-4 gap-4">
                        <div className="p-3 bg-amber-50/60 rounded-2xl border border-amber-200/60">
                          <span className="text-[10px] font-bold text-amber-700 uppercase tracking-wider block">BOARDING TIME</span>
                          <span className="text-xl font-black text-amber-900">{boardingTime}</span>
                          <span className="text-[9px] text-amber-700 block mt-0.5">Check with airline</span>
                        </div>

                        <div className="p-3 bg-slate-50 rounded-2xl border border-slate-100">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">GATE</span>
                          <span className="text-xl font-black text-slate-900">Not provided</span>
                          <span className="text-[9px] text-slate-500 block mt-0.5">Check with airline</span>
                        </div>

                        <div className="p-3 bg-slate-50 rounded-2xl border border-slate-100">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">SEAT</span>
                          <span className={`text-xl font-black ${isInfant ? 'text-amber-600 text-sm' : 'text-[#102a56]'}`}>
                            {seatNo}
                          </span>
                          <span className="text-[9px] text-slate-500 block mt-0.5">{cabinClass}</span>
                        </div>

                        <div className="p-3 bg-slate-50 rounded-2xl border border-slate-100">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">STATUS</span>
                          <span className="text-sm font-black text-emerald-600 flex items-center gap-1 mt-1">
                            <CheckCircle2 className="size-3.5" /> {ticket.status}
                          </span>
                          <span className="text-[9px] text-slate-500 block mt-0.5">E-Ticket Ready</span>
                        </div>
                      </div>

                      {/* Add-on badges if any */}
                      {ticket.addOns && ticket.addOns.length > 0 && (
                        <div className="mt-5 pt-4 border-t border-slate-100 flex flex-wrap items-center gap-2">
                          <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 mr-1">ADD-ONS:</span>
                          {ticket.addOns.map((ad, aIdx) => (
                            <span key={aIdx} className="inline-flex items-center gap-1 text-[11px] font-semibold bg-slate-100 text-slate-700 px-2.5 py-1 rounded-lg">
                              {ad.serviceType === 'BAGGAGE' && <Luggage className="size-3 text-slate-500" />}
                              {ad.serviceType === 'MEAL' && <Utensils className="size-3 text-slate-500" />}
                              {ad.serviceType === 'INSURANCE' && <ShieldCheck className="size-3 text-slate-500" />}
                              {ad.description || ad.serviceType}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Right: Tear-off Boarding Stub */}
                    <div className="p-6 bg-slate-50/80 flex flex-col justify-between relative">
                      
                      {/* Notch cutouts on top and bottom border for classic ticket look */}
                      <div className="hidden lg:block absolute -left-3 top-0 w-6 h-6 bg-[#f7f8fa] rounded-full -translate-y-1/2 border-r border-b border-slate-200" />
                      <div className="hidden lg:block absolute -left-3 bottom-0 w-6 h-6 bg-[#f7f8fa] rounded-full translate-y-1/2 border-r border-t border-slate-200" />

                      <div>
                        <div className="flex items-center justify-between pb-3 border-b border-slate-200">
                          <span className="text-xs font-black text-[#102a56]">{flightNo}</span>
                          <span className="text-[10px] font-bold text-slate-400 uppercase">PASSENGER STUB</span>
                        </div>

                        <div className="mt-3 space-y-2">
                          <div>
                            <span className="text-[9px] font-bold text-slate-400 uppercase block">PASSENGER</span>
                            <p className="text-xs font-bold text-slate-800 truncate">{passengerName}</p>
                          </div>

                          <div className="grid grid-cols-2 gap-2">
                            <div>
                              <span className="text-[9px] font-bold text-slate-400 uppercase block">FROM</span>
                              <p className="text-xs font-bold text-slate-800">{originCode}</p>
                            </div>
                            <div>
                              <span className="text-[9px] font-bold text-slate-400 uppercase block">TO</span>
                              <p className="text-xs font-bold text-slate-800">{destCode}</p>
                            </div>
                          </div>

                          <div className="grid grid-cols-2 gap-2">
                            <div>
                              <span className="text-[9px] font-bold text-slate-400 uppercase block">GATE</span>
                              <p className="text-xs font-bold text-slate-800">06</p>
                            </div>
                            <div>
                              <span className="text-[9px] font-bold text-slate-400 uppercase block">SEAT</span>
                              <p className="text-xs font-black text-[#f59e0b]">{seatNo}</p>
                            </div>
                          </div>
                        </div>
                      </div>

                      <p className="mt-6 pt-4 border-t border-slate-200 text-center text-xs font-mono">{ticketNo}</p>

                    </div>
                  </div>
                </div>
              )
            })
          )}
        </div>

        {/* Modal Bottom Footer */}
        <div className="no-print p-4 sm:p-5 bg-white border-t border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p className="text-xs text-slate-500 text-center sm:text-left">
            Demo booking confirmation only. This is not an airline boarding pass or a document for airport security.
          </p>
          <div className="flex items-center gap-3 w-full sm:w-auto">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              className="h-10 px-5 rounded-xl border-slate-200 font-bold text-xs text-slate-700 hover:bg-slate-50 cursor-pointer flex-1 sm:flex-initial"
            >
              Close
            </Button>
            <Button
              type="button"
              onClick={handlePrint}
              className="h-10 px-5 rounded-xl bg-[#102a56] hover:bg-slate-800 text-white font-bold text-xs cursor-pointer flex items-center gap-2 flex-1 sm:flex-initial"
            >
              <Printer className="size-3.5" />
              <span>Print E-Ticket</span>
            </Button>
          </div>
        </div>

      </div>
    </div>
  )
}
