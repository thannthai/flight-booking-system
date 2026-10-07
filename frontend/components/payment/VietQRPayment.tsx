'use client'

import { useState, useEffect, useRef } from 'react'
import { Check, Plane, ArrowLeft, ArrowRight, Download, Home, Loader2, QrCode, Printer, ShieldCheck, Utensils, Luggage, CheckCircle2, Ticket } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatVND } from '@/lib/format'
import type { FlightSeatResponse, BookingResponse } from '@/types/api'
import { api } from '@/lib/api'
import { UserNav } from '@/components/auth/UserNav'

export function VietQRPayment({ 
  flight, 
  returnFlight,
  seats,
  returnSeats,
  passengerData,
  bookingData,
  onBookingCreated,
  onBack, 
  onHome 
}: { 
  flight: any; 
  returnFlight?: any;
  seats: (FlightSeatResponse | null)[];
  returnSeats?: (FlightSeatResponse | null)[];
  passengerData: any;
  bookingData?: any;
  onBookingCreated: (booking: any) => void;
  onBack: () => void; 
  onHome: () => void; 
}) {
  if (!flight || !passengerData || !seats) {
    return (
      <div className="min-h-screen bg-[#f7f8fa] flex flex-col items-center justify-center p-5">
        <div className="bg-white p-8 rounded-3xl shadow-sm border border-slate-200 text-center max-w-md">
          <Plane className="size-12 text-slate-300 mx-auto mb-4" />
          <h2 className="text-xl font-bold text-slate-800 mb-2">Session Expired</h2>
          <p className="text-slate-500 text-sm mb-6">Your payment session has expired or is invalid. Please start a new search.</p>
          <Button onClick={onHome} className="w-full bg-[#102a56] hover:bg-[#1a3d75] rounded-xl h-12 font-bold">
            Start New Search
          </Button>
        </div>
      </div>
    )
  }

  const [paymentData, setPaymentData] = useState<any>(null)
  const [paymentError, setPaymentError] = useState<string | null>(null)
  const [seconds, setSeconds] = useState(899)
  const [isSuccess, setIsSuccess] = useState(false)
  const [isCreatingBooking, setIsCreatingBooking] = useState(false)
  const hasAttemptedRef = useRef(false)

  const [finalBooking, setFinalBooking] = useState<any>(null)
  const [activePassIndex, setActivePassIndex] = useState<number | 'all'>('all')

  const handlePayNow = async () => {
    setIsCreatingBooking(true)
    setPaymentError(null)
    try {
      const outboundSelections = passengerData.paxList
        .map((p: any, i: number) => ({ ...p, originalIndex: i }))
        .filter((p: any) => p.type !== 'Infant')
        .map((p: any, i: number) => {
          const paxAddOn = passengerData.passengerAddOns?.[p.originalIndex]
          const addOns: any[] = []
          if (paxAddOn?.baggage > 0) {
            addOns.push({ serviceType: 'BAGGAGE', quantity: Math.round(paxAddOn.baggage / 200000) })
          }
          if (paxAddOn?.meal) {
            addOns.push({ serviceType: 'MEAL', quantity: 1 })
          }
          if (paxAddOn?.insurance) {
            addOns.push({ serviceType: 'INSURANCE', quantity: 1 })
          }

          return {
            flightId: flight.id,
            flightSeatId: seats[i]!.id,
            passengerIndex: p.originalIndex,
            addOns: addOns.length > 0 ? addOns : undefined
          }
        })

      let seatSelections = [...outboundSelections]

      if (returnFlight && returnSeats && returnSeats.length > 0) {
        const returnSelections = passengerData.paxList
          .map((p: any, i: number) => ({ ...p, originalIndex: i }))
          .filter((p: any) => p.type !== 'Infant')
          .map((p: any, i: number) => ({
            flightId: returnFlight.id,
            flightSeatId: returnSeats[i]!.id,
            passengerIndex: p.originalIndex,
          }))
        seatSelections = [...seatSelections, ...returnSelections]
      }

      const bookingReq = {
        tripType: returnFlight ? 'ROUND_TRIP' : 'ONE_WAY',
        flightIds: returnFlight ? [flight.id, returnFlight.id] : [flight.id],
        contactInfo: {
          fullName: passengerData.contactInfo.name,
          email: passengerData.contactInfo.email,
          phone: passengerData.contactInfo.phone
        },
        passengers: passengerData.passengerRequests,
        seatSelections: seatSelections,
        voucherId: null
      }
      
      const bookingRes = await api.post<any>('/bookings', bookingReq)
      onBookingCreated(bookingRes)
    } catch (err: any) {
      console.error(err)
      setPaymentError(err.message || 'Failed to create booking')
      setIsCreatingBooking(false)
    }
  }

  const handleCancelBooking = async () => {
    if (!bookingData?.id) {
      onHome()
      return
    }
    try {
      await api.post(`/bookings/${bookingData.id}/cancel`, {})
    } catch (e) {
      console.error("Failed to cancel on backend", e)
    } finally {
      onHome()
    }
  }

  // Auto-create booking if it doesn't exist
  useEffect(() => {
    if (!bookingData?.id && !hasAttemptedRef.current) {
      hasAttemptedRef.current = true
      handlePayNow()
    }
  }, [bookingData?.id])

  // Fetch PayOS payment link
  useEffect(() => {
    if (!bookingData?.id) return;
    let mounted = true;
    api.post<any>('/payments/create-link', { bookingId: bookingData.id })
      .then(res => {
        if (mounted) {
          setPaymentData(res)
          if (res.expiredAt) {
            const diff = Math.floor((new Date(res.expiredAt).getTime() - Date.now()) / 1000)
            setSeconds(Math.max(0, diff))
          }
        }
      })
      .catch(err => {
        if (mounted) {
          console.error('Failed to create payment link:', err)
          setPaymentError('Could not initialize payment with PayOS. Please try again.')
        }
      })
    return () => { mounted = false }
  }, [bookingData?.id])

  // Countdown timer
  useEffect(() => {
    if (!bookingData?.id || isSuccess) return;
    const timer = setInterval(() => {
      setSeconds(prev => {
        if (prev <= 1) {
          clearInterval(timer)
          handleCancelBooking()
          return 0
        }
        return prev - 1
      })
    }, 1000)
    return () => clearInterval(timer)
  }, [bookingData?.id, isSuccess])

  // Polling for payment status
  useEffect(() => {
    if (!paymentData || isSuccess || paymentError || !bookingData?.id) return;

    const interval = setInterval(async () => {
      try {
        const res = await api.get<any>(`/payments/booking/${bookingData.id}`)
        if (res.paymentStatus === 'SUCCESS') {
          setIsSuccess(true)
          clearInterval(interval)
          window.scrollTo({ top: 0, behavior: 'smooth' })
        } else if (res.paymentStatus === 'EXPIRED' || res.paymentStatus === 'CANCELLED') {
          setPaymentError(`Payment has been ${res.paymentStatus.toLowerCase()}.`)
          clearInterval(interval)
        }
      } catch (e) {
        // Silently ignore network errors during polling
      }
    }, 3000)
    
    return () => clearInterval(interval)
  }, [paymentData, isSuccess, paymentError, bookingData?.id])

  const formatTime = (value: number) => {
    const m = Math.floor(value / 60)
    const s = value % 60
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }

  const totalAmount = paymentData?.amount || bookingData?.totalAmount || 0;
  const qrUrl = paymentData 
    ? `https://img.vietqr.io/image/MB-0944767622-compact2.png?amount=${totalAmount}&addInfo=${paymentData.orderCode || bookingData?.pnr || 'SKYWAY'}&accountName=${encodeURIComponent('THAI CHI THANH')}`
    : ''

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text)
  }

  useEffect(() => {
    if (isSuccess && bookingData?.id) {
      api.getBookingById(bookingData.id)
        .then(res => setFinalBooking(res))
        .catch(() => setFinalBooking(bookingData))
    }
  }, [isSuccess, bookingData?.id])

  if (isSuccess) {
    const activeBooking = finalBooking || bookingData
    const pnr = activeBooking?.pnr || 'SKYWAY'
    
    // Synthesize or use real tickets
    const resolvedTickets = (activeBooking?.tickets && activeBooking.tickets.length > 0)
      ? activeBooking.tickets
      : passengerData.paxList.map((pax: any, i: number) => {
          const seat = seats.find((_, idx) => pax.originalIndex === idx) || seats[i]
          const paxName = passengerData.passengers[i]?.name || `Passenger ${i + 1}`
          const pAddOns = passengerData.passengerAddOns?.[pax.originalIndex] || {}
          const addOnsList: any[] = []
          if (pAddOns.baggage) addOnsList.push({ serviceType: 'BAGGAGE', description: '20kg Checked Bag' })
          if (pAddOns.meal) addOnsList.push({ serviceType: 'MEAL', description: 'Hot Meal Combo' })
          if (pAddOns.insurance) addOnsList.push({ serviceType: 'INSURANCE', description: 'Travel Protection' })

          return {
            id: i + 1,
            ticketNo: `${flight.logo || 'VN'}-${pnr}-1-${String(i + 1).padStart(2, '0')}`,
            passengerName: paxName,
            passengerType: pax.type.toUpperCase(),
            seat: pax.type === 'Infant' ? null : seat,
            addOns: addOnsList
          }
        })

    const filteredTickets = activePassIndex === 'all' 
      ? resolvedTickets 
      : [resolvedTickets[activePassIndex]].filter(Boolean)

    const getBoardingTime = (depTimeStr?: string) => {
      if (!depTimeStr) return '07:15'
      try {
        const d = new Date(depTimeStr)
        if (isNaN(d.getTime())) return '07:15'
        d.setMinutes(d.getMinutes() - 45)
        return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })
      } catch {
        return '07:15'
      }
    }

    return (
      <main className="min-h-screen bg-[#f7f8fa] py-10 px-4 sm:px-6 print:p-0 print:bg-white">
        <style jsx global>{`
          @media print {
            body * {
              visibility: hidden;
            }
            #post-payment-print-area, #post-payment-print-area * {
              visibility: visible;
            }
            #post-payment-print-area {
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

        <div className="max-w-4xl mx-auto space-y-6">
          
          {/* Success Banner (no-print) */}
          <div className="no-print bg-white rounded-3xl p-6 sm:p-8 shadow-sm border border-emerald-100 flex flex-col sm:flex-row items-center justify-between gap-6">
            <div className="flex items-center gap-4 text-center sm:text-left">
              <div className="size-14 rounded-2xl bg-emerald-50 border border-emerald-200 flex items-center justify-center text-emerald-600 shrink-0">
                <CheckCircle2 className="size-8" />
              </div>
              <div>
                <span className="text-[10px] font-bold uppercase tracking-widest text-emerald-600 bg-emerald-50 px-2.5 py-0.5 rounded-full border border-emerald-200">
                  PAYMENT SUCCESSFUL
                </span>
                <h1 className="text-2xl sm:text-3xl font-black text-slate-900 mt-1">Booking Confirmed!</h1>
                <p className="text-xs text-slate-500 mt-1">
                  PNR Reference: <strong className="text-[#102a56] font-mono font-bold text-sm">{pnr}</strong> · Electronic tickets and boarding passes are ready below.
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3 shrink-0">
              <Button onClick={() => window.print()} className="h-11 px-5 rounded-xl bg-[#102a56] hover:bg-slate-800 text-white font-bold text-xs cursor-pointer flex items-center gap-2">
                <Printer className="size-4" />
                <span>Print Passes</span>
              </Button>
              <Button onClick={onHome} variant="outline" className="h-11 px-5 rounded-xl border-slate-200 font-bold text-xs text-slate-700 hover:bg-slate-50 cursor-pointer">
                <Home className="size-4 mr-1.5" />
                Home
              </Button>
            </div>
          </div>

          {/* Passenger Filter Tabs (no-print) */}
          {resolvedTickets.length > 1 && (
            <div className="no-print flex items-center gap-2 overflow-x-auto pb-1">
              <button
                type="button"
                onClick={() => setActivePassIndex('all')}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer ${
                  activePassIndex === 'all'
                    ? 'bg-[#102a56] text-white shadow-xs'
                    : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                }`}
              >
                All Passengers ({resolvedTickets.length})
              </button>
              {resolvedTickets.map((t: any, idx: number) => {
                const paxName = t.passengerName || `Passenger ${idx + 1}`
                const isSelected = activePassIndex === idx
                const seatLabel = t.passengerType === 'INFANT' ? 'Lap Infant' : t.seat?.seatNo ? `Seat ${t.seat.seatNo}` : 'Seated'
                return (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setActivePassIndex(idx)}
                    className={`px-4 py-2 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer ${
                      isSelected
                        ? 'bg-[#102a56] text-white shadow-xs'
                        : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                    }`}
                  >
                    {paxName} ({seatLabel})
                  </button>
                )
              })}
            </div>
          )}

          {/* Boarding Passes Area */}
          <div id="post-payment-print-area" className="space-y-6">
            {filteredTickets.map((ticket: any, idx: number) => {
              const ticketFlight = ticket.flight || flight
              const airlineName = ticketFlight?.airline?.name || flight.airline || 'Vietnam Airlines'
              const airlineLogo = ticketFlight?.airline?.code || flight.logo || 'VN'
              const flightNo = ticketFlight?.flightNo || flight.code || 'VN 123'
              const aircraft = ticketFlight?.aircraft?.model || flight.aircraft || 'Airbus A321'
              const originCode = ticketFlight?.origin?.iataCode || 'SGN'
              const originCity = ticketFlight?.origin?.city || 'Ho Chi Minh City'
              const destCode = ticketFlight?.dest?.iataCode || 'HAN'
              const destCity = ticketFlight?.dest?.city || 'Hanoi'
              const depTime = ticketFlight?.departureTime ? formatTime(ticketFlight.departureTime) : flight.depart
              const arrTime = ticketFlight?.arrivalTime ? formatTime(ticketFlight.arrivalTime) : flight.arrive
              const paxName = (ticket.passengerName || ticket.passenger?.fullName || 'PASSENGER').toUpperCase()
              const isInfant = ticket.passengerType === 'INFANT'
              const seatNo = isInfant ? 'INFANT (LAP)' : ticket.seat?.seatNo || 'ASSIGNED'
              const cabinClass = isInfant ? 'LAP INFANT' : ticket.seat?.cabinClass || 'ECONOMY'
              const ticketNo = ticket.ticketNo || `${airlineLogo}-${pnr}-1-${String(idx + 1).padStart(2, '0')}`
              const boardingTime = getBoardingTime(ticketFlight?.departureTime || flight.departureTime)

              return (
                <div 
                  key={idx} 
                  className="bg-white rounded-3xl shadow-md border border-slate-200/90 overflow-hidden print-break"
                >
                  {/* Top Bar */}
                  <div className="bg-[#102a56] text-white px-6 py-4 flex flex-wrap items-center justify-between gap-4 border-b-2 border-[#f59e0b]">
                    <div className="flex items-center gap-3">
                      <div className="size-9 bg-white rounded-lg p-1 flex items-center justify-center">
                        <img 
                          src={`/airlines/${airlineLogo}.svg`} 
                          alt={airlineName} 
                          className="h-full w-full object-contain"
                          onError={(e) => { (e.target as HTMLElement).style.display = 'none' }} 
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
                        <span className="block text-[10px] uppercase font-bold tracking-wider text-slate-300">BOARDING PASS</span>
                        <span className="text-xs font-mono font-bold text-amber-300">{ticketNo}</span>
                      </div>
                    </div>
                  </div>

                  {/* Body: Left Main + Right Stub */}
                  <div className="grid lg:grid-cols-[1fr_260px] relative">
                    
                    {/* Left Section */}
                    <div className="p-6 sm:p-8 border-b lg:border-b-0 lg:border-r border-slate-200 relative">
                      
                      {/* Name & PNR */}
                      <div className="flex flex-wrap items-start justify-between gap-4 pb-6 border-b border-slate-100">
                        <div>
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest block">PASSENGER NAME</span>
                          <h3 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight mt-0.5">
                            {paxName}
                          </h3>
                          <div className="flex items-center gap-2 mt-1">
                            <span className="text-[11px] font-semibold text-slate-500 uppercase">
                              Type: <strong className="text-slate-800">{ticket.passengerType}</strong>
                            </span>
                            {isInfant && (
                              <span className="text-[10px] font-bold bg-amber-100 text-amber-800 px-2 py-0.5 rounded-full">
                                Accompanied on Adult Lap
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="text-right">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest block">BOOKING REF (PNR)</span>
                          <span className="text-2xl font-black font-mono tracking-wider text-[#102a56]">
                            {pnr}
                          </span>
                        </div>
                      </div>

                      {/* Route */}
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
                          <span className="text-[11px] font-medium text-slate-500 mt-1">{flight.duration}</span>
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
                          <span className="text-[9px] text-amber-700 block mt-0.5">Closes 15m prior</span>
                        </div>

                        <div className="p-3 bg-slate-50 rounded-2xl border border-slate-100">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">GATE</span>
                          <span className="text-xl font-black text-slate-900">Gate 06</span>
                          <span className="text-[9px] text-slate-500 block mt-0.5">Terminal 1</span>
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
                            <CheckCircle2 className="size-3.5" /> ISSUED
                          </span>
                          <span className="text-[9px] text-slate-500 block mt-0.5">Boarding Pass Ready</span>
                        </div>
                      </div>

                      {/* Add-ons */}
                      {ticket.addOns && ticket.addOns.length > 0 && (
                        <div className="mt-5 pt-4 border-t border-slate-100 flex flex-wrap items-center gap-2">
                          <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 mr-1">ADD-ONS:</span>
                          {ticket.addOns.map((ad: any, aIdx: number) => (
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

                    {/* Right: Tear-off Stub */}
                    <div className="p-6 bg-slate-50/80 flex flex-col justify-between relative">
                      
                      {/* Notch cutouts */}
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
                            <p className="text-xs font-bold text-slate-800 truncate">{paxName}</p>
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

                      {/* Barcode representation */}
                      <div className="mt-6 pt-4 border-t border-slate-200 flex flex-col items-center">
                        <div className="w-full flex items-center justify-between gap-[2px] h-10 px-2 bg-white rounded-lg border border-slate-200 py-1 mb-2">
                          {[3, 1, 2, 4, 1, 3, 2, 1, 4, 2, 3, 1, 2, 1, 3, 4, 1, 2, 3, 1, 2, 4, 1, 3].map((w, i) => (
                            <div 
                              key={i} 
                              className="bg-slate-900 h-full" 
                              style={{ width: `${w * 2}px` }} 
                            />
                          ))}
                        </div>
                        <span className="text-[10px] font-mono font-bold tracking-wider text-slate-500">
                          {ticketNo}
                        </span>
                      </div>

                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          {/* Action buttons bar */}
          <div className="no-print p-6 bg-white rounded-3xl border border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-4">
            <p className="text-xs text-slate-500 text-center sm:text-left">
              An electronic copy of this booking confirmation has also been recorded in your Skyway account.
            </p>
            <div className="flex items-center gap-3 w-full sm:w-auto">
              <Button onClick={onHome} variant="outline" className="h-11 px-6 rounded-xl font-bold text-xs text-slate-700 hover:bg-slate-50 cursor-pointer flex-1 sm:flex-initial">
                <Home className="size-4 mr-2" />
                Back to Home
              </Button>
              <Button onClick={() => window.print()} className="h-11 px-6 rounded-xl bg-[#102a56] hover:bg-slate-800 text-white font-bold text-xs cursor-pointer flex-1 sm:flex-initial flex items-center gap-2">
                <Printer className="size-4" />
                Print Boarding Passes
              </Button>
            </div>
          </div>

        </div>
      </main>
    )
  }

  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950 pb-20">
      {/* Header */}
      <header className="bg-white sticky top-0 z-40">
        <div className="border-b border-slate-200">
          <div className="mx-auto flex h-[72px] max-w-7xl items-center justify-between px-5 lg:px-8">
            <button onClick={onHome} className="flex items-center gap-3 cursor-pointer" aria-label="Skyway home">
              <span className="flex size-10 items-center justify-center rounded-xl bg-[#102a56] text-white">
                <Plane className="size-5 -rotate-12" />
              </span>
              <span className="text-xl font-bold tracking-tight">
                skyway<span className="text-[#f59e0b]">.</span>
              </span>
            </button>
            <div className="flex items-center gap-3">
              <UserNav />
            </div>
          </div>
        </div>
        
        {/* Sub-bar Stepper */}
        <div className="hidden lg:block border-b border-slate-200 bg-slate-50/50">
          <div className="mx-auto flex h-11 max-w-4xl items-center justify-center gap-4 text-sm font-semibold text-slate-400">
            <button onClick={onHome} className="flex items-center gap-2 text-emerald-600 font-bold hover:underline cursor-pointer">
              <Check className="size-4" /> Search
            </button>
            <span className="h-px w-8 bg-slate-300" />
            <button onClick={onHome} className="flex items-center gap-2 text-emerald-600 font-bold hover:underline cursor-pointer">
              <Check className="size-4" /> Flights
            </button>
            <span className="h-px w-8 bg-slate-300" />
            <button onClick={() => { if (!bookingData?.id) { onBack(); setTimeout(onBack, 0) } }} className={`flex items-center gap-2 font-bold hover:underline cursor-pointer ${bookingData?.id ? 'text-emerald-600 opacity-60 cursor-not-allowed hover:no-underline' : 'text-emerald-600'}`}>
              <Check className="size-4" /> Passengers
            </button>
            <span className="h-px w-8 bg-slate-300" />
            <button onClick={() => { if (!bookingData?.id) onBack() }} className={`flex items-center gap-2 font-bold hover:underline cursor-pointer ${bookingData?.id ? 'text-emerald-600 opacity-60 cursor-not-allowed hover:no-underline' : 'text-emerald-600'}`}>
              <Check className="size-4" /> Seats
            </button>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2 text-[#102a56] font-bold">
              <div className="size-5 rounded-full bg-[#102a56] text-white flex items-center justify-center text-[10px] shadow-[0_0_0_3px_rgba(16,42,86,0.1)]">5</div> Payment
            </span>
          </div>
        </div>
      </header>

      {/* Selected Flight Bar */}
      <section className="bg-[#102a56] text-white border-b-4 border-[#f59e0b]">
        <div className="mx-auto max-w-7xl px-5 py-6 lg:px-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            {!bookingData?.id && (
              <button onClick={onBack} className="flex items-center gap-2 text-slate-300 hover:text-white text-[11px] font-bold uppercase tracking-widest mb-4 cursor-pointer transition-colors">
                <ArrowLeft className="size-3.5" /> Back to Seat Selection
              </button>
            )}
            <p className="text-[10px] font-bold uppercase tracking-[.2em] text-slate-300/80 mb-3">
              {returnFlight ? 'Selected flights (Round-Trip)' : 'Selected flight'}
            </p>
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-4">
                <img src={`/airlines/${flight.logo}.svg`} alt={flight.airline} className="h-8 w-12 object-contain bg-white rounded-lg p-1" />
                <div>
                  <p className="text-sm font-bold">
                    {returnFlight && <span className="text-[10px] px-2 py-0.5 rounded bg-blue-500/30 text-blue-200 mr-2 uppercase font-black tracking-wider">Outbound</span>}
                    {flight.airline} · {flight.code} 
                    <span className="ml-2 font-normal text-slate-300 text-xs hidden sm:inline-block">
                      {flight.aircraft}
                    </span>
                  </p>
                  <p className="text-xs text-slate-300 mt-0.5 flex items-center gap-1.5 flex-wrap">
                    {flight.depart} - {flight.arrive} 
                  </p>
                </div>
              </div>
              {returnFlight && (
                <div className="flex items-center gap-4 pt-2 border-t border-white/10">
                  <img src={`/airlines/${returnFlight.logo}.svg`} alt={returnFlight.airline} className="h-8 w-12 object-contain bg-white rounded-lg p-1" />
                  <div>
                    <p className="text-sm font-bold">
                      <span className="text-[10px] px-2 py-0.5 rounded bg-amber-500/30 text-amber-200 mr-2 uppercase font-black tracking-wider">Return</span>
                      {returnFlight.airline} · {returnFlight.code} 
                      <span className="ml-2 font-normal text-slate-300 text-xs hidden sm:inline-block">
                        {returnFlight.aircraft}
                      </span>
                    </p>
                    <p className="text-xs text-slate-300 mt-0.5 flex items-center gap-1.5 flex-wrap">
                      {returnFlight.depart} - {returnFlight.arrive} 
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
          
          {/* Hold Timer & Cancel */}
          <div className="flex flex-col sm:flex-row sm:items-center gap-3">
            {bookingData?.id && (
              <>
                <div className="flex items-center gap-3 bg-white/10 rounded-xl px-4 py-3 border border-white/5 shadow-sm">
                  <span className="flex size-2 rounded-full bg-amber-400 animate-pulse" />
                  <span className="text-sm font-medium">
                    Payment expires in <strong className="text-amber-400 ml-1">{formatTime(seconds)}</strong>
                  </span>
                </div>
                <button onClick={handleCancelBooking} className="text-sm px-4 py-3 bg-red-500/10 text-red-400 hover:bg-red-500/20 hover:text-red-300 rounded-xl font-bold transition-colors cursor-pointer border border-red-500/20">
                  Cancel Booking
                </button>
              </>
            )}
          </div>
        </div>
      </section>

      {/* Main Content */}
      <div className="mx-auto max-w-4xl px-5 py-12 lg:px-8">
        
        <div className="text-center mb-10">
            <p className="text-[10px] font-bold uppercase tracking-[.2em] text-slate-400 mb-2">
              {bookingData?.id ? 'SECURE PAYMENT' : 'REVIEW & CHECKOUT'}
            </p>
            <h2 className="text-3xl font-black text-[#102a56]">
              {bookingData?.id ? 'Scan to Pay via VietQR' : 'Securing your Booking...'}
            </h2>
          </div>

        <div className="bg-white rounded-3xl shadow-lg shadow-slate-200/50 border border-slate-200 overflow-hidden">
          <div className="grid md:grid-cols-2">
            
            {/* QR Section */}
            <div className="p-8 lg:p-10 border-b md:border-b-0 md:border-r border-slate-100 flex flex-col items-center justify-center bg-slate-50/50">
              <div className="flex items-center justify-center gap-4 mb-8">
                <div className="text-[10px] font-bold text-emerald-600 bg-emerald-50 px-2.5 py-1 rounded-full border border-emerald-100">NAPAS247</div>
                <div className="text-[10px] font-bold text-emerald-600 bg-emerald-50 px-2.5 py-1 rounded-full border border-emerald-100">MB BANK</div>
              </div>

              <div className="bg-white p-4 rounded-3xl shadow-sm border border-slate-100 mb-6 flex items-center justify-center min-h-[256px] min-w-[256px]">
                {!bookingData?.id ? (
                  paymentError ? (
                    <div className="flex flex-col items-center justify-center text-center p-6 h-64 w-64 gap-4">
                      <div className="size-12 rounded-full bg-red-50 flex items-center justify-center text-red-500 mb-2">
                        <span className="text-xl font-bold">!</span>
                      </div>
                      <span className="text-xs font-bold text-slate-500">Booking failed</span>
                      <Button onClick={() => { setPaymentError(null); hasAttemptedRef.current = false; }} variant="outline" size="sm" className="mt-2 text-xs h-8">Try Again</Button>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center justify-center text-center p-6 h-64 w-64 gap-6">
                      <Loader2 className="size-8 animate-spin text-slate-300" />
                      <span className="text-xs font-bold text-slate-400 uppercase tracking-widest">Creating booking...</span>
                    </div>
                  )
                ) : qrUrl ? (
                  <img 
                    src={qrUrl} 
                    alt="VietQR" 
                    className="w-56 h-56 lg:w-64 lg:h-64 object-contain rounded-xl animate-in fade-in"
                    onError={(e) => { 
                      (e.target as HTMLImageElement).src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 24 24" fill="none" stroke="%23cbd5e1" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><rect width="8" height="8" x="7" y="7"/></svg>' 
                    }}
                  />
                ) : (
                  <div className="flex flex-col items-center justify-center gap-3 text-slate-400 w-56 h-56 lg:w-64 lg:h-64">
                    <Loader2 className="size-8 animate-spin" />
                    <span className="text-xs font-semibold">Generating QR...</span>
                  </div>
                )}
              </div>

              <p className="text-sm font-medium text-slate-500 text-center flex items-center gap-2">
                <QrCode className="size-4 shrink-0" />
                {bookingData?.id ? 'Scan with any banking app to complete payment instantly' : 'Click Confirm & Pay to generate your VietQR payment code'}
              </p>
            </div>

            {/* Transfer Details Section */}
            <div className="p-8 lg:p-10 flex flex-col">
              {!bookingData?.id ? (
                <>
                  <div className="flex-1 flex flex-col items-center justify-center text-center">
                    <Plane className={`size-16 mb-6 -rotate-12 ${paymentError ? 'text-red-200' : 'text-slate-200 animate-pulse'}`} />
                    <h3 className="text-lg font-bold text-[#102a56] mb-2">{paymentError ? 'Action Required' : 'Please wait'}</h3>
                    <p className="text-slate-500 text-sm">{paymentError ? 'We could not secure your booking. Please try again.' : 'We are finalizing your reservation and generating the payment QR code...'}</p>
                  </div>
                  {paymentError && (
                    <div className="mt-8 w-full flex flex-col items-center justify-center p-6 bg-red-50 rounded-2xl border border-red-200 text-center">
                      <p className="text-sm font-bold text-red-600">{paymentError}</p>
                    </div>
                  )}
                </>
              ) : (
                <>
                  <h3 className="text-lg font-bold text-[#102a56] mb-8">Transfer details</h3>
                  
                  <div className="flex-1 space-y-6 text-sm">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-4">
                      <span className="text-slate-500 font-medium">Bank</span>
                      <span className="font-bold text-slate-800 sm:text-right">MB Bank (Military Commercial Joint Stock Bank)</span>
                    </div>
                    
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-4">
                      <span className="text-slate-500 font-medium">Account Number</span>
                      <div className="flex items-center sm:justify-end gap-3">
                        <span className="font-bold text-slate-800 tracking-wider">0944767622</span>
                        <button onClick={() => handleCopy('0944767622')} className="text-[#2563eb] text-xs font-bold hover:underline cursor-pointer">Copy</button>
                      </div>
                    </div>

                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-4">
                      <span className="text-slate-500 font-medium">Account Name</span>
                      <span className="font-bold text-slate-800">THAI CHI THANH</span>
                    </div>

                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-4">
                      <span className="text-slate-500 font-medium">Amount</span>
                      <div className="flex items-center sm:justify-end gap-3">
                        <span className="font-bold text-[#f59e0b] text-lg tabular-nums">{paymentData ? formatVND(paymentData.amount) : '...'}</span>
                      </div>
                    </div>

                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4">
                      <span className="text-slate-500 font-medium">Transfer Note</span>
                      <div className="flex items-center sm:justify-end gap-3">
                        <span className="font-bold text-slate-800 bg-slate-100 px-3 py-1 rounded-lg">{paymentData?.orderCode || '...'}</span>
                        {paymentData && <button onClick={() => handleCopy(paymentData.orderCode.toString())} className="text-[#2563eb] text-xs font-bold hover:underline cursor-pointer">Copy</button>}
                      </div>
                    </div>
                  </div>

                  {paymentError && (
                    <div className="mt-8 w-full flex flex-col items-center justify-center p-6 bg-red-50 rounded-2xl border border-red-200 text-center">
                      <p className="text-sm font-bold text-red-600 mb-2">{paymentError}</p>
                      <Button onClick={onHome} className="bg-red-100 hover:bg-red-200 text-red-700 h-10 px-6 rounded-xl text-xs font-bold cursor-pointer">
                        Return to Home
                      </Button>
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </main>
  )
}
