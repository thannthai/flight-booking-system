'use client'

import Image from 'next/image'

import { SkywayBrand } from '@/components/SkywayBrand'

import { useState, useEffect, useRef } from 'react'
import { Plane, Check, ArrowRight, ArrowLeft, Armchair } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatVND } from '@/lib/format'
import { assignSeats } from '@/lib/seats'
import { api, ApiError, getAccessToken } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { UserNav } from '@/components/auth/UserNav'
import type { FlightSeatMapResponse, FlightSeatResponse } from '@/types/api'

import type { AdaptedFlight } from '@/lib/flight'
import { priceBooking } from '@/lib/pricing'
import { passengerType, type PassengerData } from '@/lib/checkout-model'
import type { BookingResponse } from '@/types/api'

function parseSeatMap(seats: FlightSeatResponse[]): [number, FlightSeatResponse[]][] {
  const rows = new Map<number, FlightSeatResponse[]>()
  seats.forEach(seat => {
    const rowNum = parseInt(seat.seatNo.replace(/\D/g, ''))
    if (!rows.has(rowNum)) rows.set(rowNum, [])
    rows.get(rowNum)!.push(seat)
  })
  const sortedRows = Array.from(rows.entries()).sort((a, b) => a[0] - b[0])
  sortedRows.forEach(row => {
    row[1].sort((a, b) => a.seatNo.localeCompare(b.seatNo))
  })
  return sortedRows
}

export function SeatSelection(props: Omit<Parameters<typeof SeatSelectionForm>[0], 'flight' | 'passengerData'> & { flight: AdaptedFlight | null; passengerData: PassengerData | null }) {
  if (!props.flight || !props.passengerData?.paxList) return <main className="p-8"><p>Choose a flight and enter passengers first.</p><Button onClick={props.onHome}>Start New Search</Button></main>
  return <SeatSelectionForm {...props} flight={props.flight} passengerData={props.passengerData} />
}

function SeatSelectionForm({
  flight, 
  returnFlight,
  passengerData,
  bookingData,
  cabinClass = 'ECONOMY',
  initialSeats = [],
  initialReturnSeats = [],
  onBack, 
  onHome, 
  onContinue 
}: { 
  flight: AdaptedFlight;
  returnFlight?: AdaptedFlight | null;
  passengerData: PassengerData;
  bookingData?: BookingResponse | null;
  cabinClass?: 'ECONOMY' | 'BUSINESS';
  initialSeats?: (FlightSeatResponse | null)[];
  initialReturnSeats?: (FlightSeatResponse | null)[];
  onBack: () => void; 
  onHome: () => void; 
  onContinue: (seats: FlightSeatResponse[], booking: BookingResponse | null, returnSeats?: FlightSeatResponse[], expiresAt?: string) => void;
}) {
  const { openAuthModal } = useAuth()
  
  // Filter out infants because they don't get seats
  const eligiblePax = passengerData.paxList
    .map((p, i) => ({ ...p, name: passengerData.passengers[i].name, originalIndex: i }))
    .filter((p) => p.type !== 'Infant')

  // Dual-leg selection state
  const [activeLeg, setActiveLeg] = useState<'outbound' | 'return'>('outbound')
  const [outboundSeats, setOutboundSeats] = useState<(FlightSeatResponse | null)[]>(
    Array.from({ length: eligiblePax.length }, (_, i) => initialSeats[i] || null)
  )
  const [returnSeats, setReturnSeats] = useState<(FlightSeatResponse | null)[]>(
    Array.from({ length: eligiblePax.length }, (_, i) => initialReturnSeats[i] || null)
  )
  const [activePaxIndex, setActivePaxIndex] = useState(0)

  const [outboundSeatMap, setOutboundSeatMap] = useState<[number, FlightSeatResponse[]][]>([])
  const [returnSeatMap, setReturnSeatMap] = useState<[number, FlightSeatResponse[]][]>([])
  const [loading, setLoading] = useState(true)
  const [holding, setHolding] = useState(false)
  const inFlight = useRef(false)
  const [seatReload, setSeatReload] = useState(0)
  const [seatLoadError, setSeatLoadError] = useState(false)
  const [holdError, setHoldError] = useState<string | null>(null)

  const currentFlight = (activeLeg === 'return' && returnFlight) ? returnFlight : flight
  const currentSeatMap = (activeLeg === 'return' && returnFlight) ? returnSeatMap : outboundSeatMap
  const currentSeats = activeLeg === 'return' ? returnSeats : outboundSeats
  const setCurrentSeats = (updater: (prev: (FlightSeatResponse | null)[]) => (FlightSeatResponse | null)[]) => {
    if (activeLeg === 'return') {
      setReturnSeats(updater)
    } else {
      setOutboundSeats(updater)
    }
  }

  // Fetch seat map(s)
  const returnFlightId = returnFlight?.id
  const seatQuery = JSON.stringify([flight.id, returnFlightId, seatReload, cabinClass])
  const [previousQuery, setPreviousQuery] = useState(seatQuery)
  if (previousQuery !== seatQuery) {
    setPreviousQuery(seatQuery)
    setLoading(true)
    setSeatLoadError(false)
  }
  useEffect(() => {
    let active = true
    const fetchPromises = [
      api.get<FlightSeatMapResponse>(`/flights/${flight.id}/seats`).then(res => {
        if (!active) return
        setOutboundSeatMap(parseSeatMap(res.seats))
        setOutboundSeats(old => old.map(s => res.seats.find(latest => latest.id === s?.id && latest.status !== 'BOOKED' && latest.cabinClass === cabinClass) || null))
      })
    ]

    if (returnFlightId != null) {
      fetchPromises.push(
        api.get<FlightSeatMapResponse>(`/flights/${returnFlightId}/seats`).then(res => {
          if (!active) return
          setReturnSeatMap(parseSeatMap(res.seats))
          setReturnSeats(old => old.map(s => res.seats.find(latest => latest.id === s?.id && latest.status !== 'BOOKED' && latest.cabinClass === cabinClass) || null))
        })
      )
    }

    Promise.all(fetchPromises)
      .catch(() => {
        if (active) setSeatLoadError(true)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [flight.id, returnFlightId, seatReload, cabinClass])

  const toggleSeat = (seat: FlightSeatResponse) => {
    const isAlreadySelected = currentSeats.some(s => s?.id === seat.id)
    if (!isAlreadySelected && (seat.status !== 'AVAILABLE' || seat.cabinClass !== cabinClass)) return
    
    const next = [...currentSeats]
    const existing = next.findIndex(s => s?.id === seat.id)
    if (existing !== -1) next[existing] = null
    else next[activePaxIndex] = seat
    setCurrentSeats(() => next)
    const unassigned = next.findIndex(s => s === null)
    if (unassigned !== -1) setActivePaxIndex(unassigned)
  }

  const outboundComplete = outboundSeats.length === eligiblePax.length && outboundSeats.every(Boolean)
  const returnComplete = !returnFlight || (returnSeats.length === eligiblePax.length && returnSeats.every(Boolean))
  const needsReturn = outboundComplete && !returnComplete
  const isAllSeatsSelected = outboundSeats.every(s => s !== null) && (!returnFlight || returnSeats.every(s => s !== null))

  const handleAutoAssign = () => {
    setOutboundSeats(assignSeats(outboundSeatMap.flatMap(([, seats]) => seats), outboundSeats, cabinClass))
    if (returnFlight) setReturnSeats(assignSeats(returnSeatMap.flatMap(([, seats]) => seats), returnSeats, cabinClass))
  }
  

  const handleContinue = async () => {
    if (inFlight.current) return
    if (!getAccessToken()) {
      openAuthModal('login', () => {
        handleContinue()
      })
      return
    }

    if (!outboundSeats.every(s => s !== null)) {
      setActiveLeg('outbound')
      setActivePaxIndex(outboundSeats.findIndex(s => s === null))
      setHoldError('Please select outbound seats for all passengers.')
      return
    }

    if (returnFlight && !returnSeats.every(s => s !== null)) {
      setActiveLeg('return')
      setActivePaxIndex(returnSeats.findIndex(s => s === null))
      setHoldError('Please select return seats for all passengers.')
      return
    }

    if (bookingData?.id) {
      onContinue(outboundSeats as FlightSeatResponse[], bookingData, returnFlight ? returnSeats as FlightSeatResponse[] : undefined)
      return
    }

    inFlight.current = true
    setHolding(true)
    setHoldError(null)
    
    try {
      const seatIds = [
        ...outboundSeats.map(s => s!.id),
        ...(returnFlight ? returnSeats.map(s => s!.id) : [])
      ]
      const hold = await api.post<import('@/types/api').HoldSeatsResponse>('/bookings/hold-seats', { seatIds })
      
      onContinue(outboundSeats as FlightSeatResponse[], null, returnFlight ? returnSeats as FlightSeatResponse[] : undefined, hold.expiresAt)
    } catch (err) {
      setHolding(false)

      const isAuthIssue = 
        err instanceof ApiError && (err.status === 401 || err.errorCode === 'UNAUTHORIZED')

      if (isAuthIssue) {
        setHoldError(null)
        openAuthModal('login', () => {
          handleContinue()
        })
        return
      }

      setOutboundSeats(new Array(eligiblePax.length).fill(null))
      setReturnSeats(new Array(eligiblePax.length).fill(null))
      setSeatReload(n => n + 1)
      setHoldError(err instanceof Error ? err.message : 'Failed to hold seats. Choose seats again from the updated map.')
    } finally { inFlight.current = false; setHolding(false) }
  }

  // Price calculation
  const outboundBasePrice = flight.basePrice
  const returnBasePrice = returnFlight ? returnFlight.basePrice : 0
  const passengerTypes = passengerData.paxList.map(p => passengerType(p.type))
  const selectedPrices = (list: (FlightSeatResponse | null)[], base: number) => eligiblePax.map((p: { originalIndex: number }, i: number) => ({ passengerIndex: p.originalIndex, price: list[i]?.price ?? base }))
  const estimate = priceBooking(returnFlight ? [flight.basePrice, returnFlight.basePrice] : [flight.basePrice], passengerTypes,
    [...selectedPrices(outboundSeats, flight.basePrice), ...(returnFlight ? selectedPrices(returnSeats, returnFlight.basePrice) : [])], passengerData.passengerAddOns)
  const finalTotal = estimate.totalAmount

  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950 pb-20">
      {/* Header */}
      <header className="bg-white sticky top-0 z-40">
        <div className="border-b border-slate-200">
          <div className="mx-auto flex h-[72px] max-w-7xl items-center justify-between px-5 lg:px-8">
            <button onClick={onHome} className="flex items-center gap-3 cursor-pointer" aria-label="Skyway home">
              <SkywayBrand />
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
            <button onClick={onBack} className="flex items-center gap-2 text-emerald-600 font-bold hover:underline cursor-pointer">
              <Check className="size-4" /> Passengers
            </button>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2 text-[#102a56] font-bold">
              <div className="size-5 rounded-full bg-[#102a56] text-white flex items-center justify-center text-[10px] shadow-[0_0_0_3px_rgba(16,42,86,0.1)]">4</div> Seats
            </span>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2 text-slate-400"><div className="size-5 rounded-full border border-slate-300 flex items-center justify-center text-[10px]">5</div> Payment</span>
          </div>
        </div>
      </header>

      {/* Selected Flight Bar */}
      <section className="bg-[#102a56] text-white border-b-4 border-[#f59e0b]">
        <div className="mx-auto max-w-7xl px-5 py-6 lg:px-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            <button onClick={onBack} className="flex items-center gap-2 text-slate-300 hover:text-white text-[11px] font-bold uppercase tracking-widest mb-4 cursor-pointer transition-colors">
              <ArrowLeft className="size-3.5" /> Back to Passengers
            </button>
            
            <p className="text-[10px] font-bold uppercase tracking-[.2em] text-slate-300/80 mb-3">
              {returnFlight ? 'Selected Flights (Round-Trip)' : 'Selected Flight'}
            </p>

            <div className="flex flex-col gap-3">
              {/* Outbound */}
              <div className={`flex items-center gap-4 p-2 rounded-xl transition-all ${activeLeg === 'outbound' ? 'bg-white/10 ring-1 ring-amber-400' : 'opacity-80'}`}>
                <Image unoptimized width={48} height={32} src={`/airlines/${flight.logo}.svg`} alt={flight.airline} className="h-8 w-12 object-contain bg-white rounded-lg p-1" />
                <div>
                  <p className="text-sm font-bold">
                    <span className="text-[10px] px-2 py-0.5 rounded bg-blue-500/30 text-blue-200 mr-2 uppercase font-black tracking-wider">Outbound</span>
                    {flight.airline} · {flight.code} 
                    <span className="ml-2 font-normal text-slate-300 text-xs hidden sm:inline-block">
                      {flight.aircraft}
                    </span>
                  </p>
                  <p className="text-xs text-slate-300 mt-0.5 flex items-center gap-1.5 flex-wrap">
                    {flight.depart} - {flight.arrive} • {outboundSeats.filter(Boolean).length}/{eligiblePax.length} seats chosen
                  </p>
                </div>
              </div>

              {/* Return (if present) */}
              {returnFlight && (
                <div className={`flex items-center gap-4 p-2 rounded-xl transition-all ${activeLeg === 'return' ? 'bg-white/10 ring-1 ring-amber-400' : 'opacity-80'}`}>
                  <Image unoptimized width={48} height={32} src={`/airlines/${returnFlight.logo}.svg`} alt={returnFlight.airline} className="h-8 w-12 object-contain bg-white rounded-lg p-1" />
                  <div>
                    <p className="text-sm font-bold">
                      <span className="text-[10px] px-2 py-0.5 rounded bg-amber-500/30 text-amber-200 mr-2 uppercase font-black tracking-wider">Return</span>
                      {returnFlight.airline} · {returnFlight.code} 
                      <span className="ml-2 font-normal text-slate-300 text-xs hidden sm:inline-block">
                        {returnFlight.aircraft}
                      </span>
                    </p>
                    <p className="text-xs text-slate-300 mt-0.5 flex items-center gap-1.5 flex-wrap">
                      {returnFlight.depart} - {returnFlight.arrive} • {returnSeats.filter(Boolean).length}/{eligiblePax.length} seats chosen
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      <div className="mx-auto grid max-w-7xl gap-8 px-5 py-8 lg:grid-cols-[1fr_360px] lg:px-8 items-start">
        <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm overflow-hidden flex flex-col items-center">
          
          {/* Round-trip leg tabs */}
          {returnFlight && (
            <div className="flex rounded-2xl bg-slate-100 p-1 mb-8 max-w-md w-full border border-slate-200">
              <button
                type="button"
                onClick={() => { setActiveLeg('outbound'); setActivePaxIndex(0); }}
                className={`flex-1 py-2.5 px-4 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-2 cursor-pointer ${
                  activeLeg === 'outbound' 
                    ? 'bg-[#102a56] text-white shadow-sm' 
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Plane className="size-3.5" />
                <span>Outbound: {outboundSeats.filter(Boolean).length}/{eligiblePax.length}</span>
              </button>
              <button
                type="button"
                onClick={() => { setActiveLeg('return'); setActivePaxIndex(0); }}
                className={`flex-1 py-2.5 px-4 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-2 cursor-pointer ${
                  activeLeg === 'return' 
                    ? 'bg-[#102a56] text-white shadow-sm' 
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Plane className="size-3.5 rotate-180" />
                <span>Return: {returnSeats.filter(Boolean).length}/{eligiblePax.length}</span>
              </button>
            </div>
          )}

          <div className="mb-8 text-center">
            <h2 className="text-2xl font-bold text-[#102a56]">
              {returnFlight 
                ? (activeLeg === 'outbound' ? 'Select Outbound Flight Seats' : 'Select Return Flight Seats')
                : 'Choose your seats'}
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              {currentFlight.airline} ({currentFlight.code}) · Select <strong className="text-slate-800">{eligiblePax.length} seat{eligiblePax.length > 1 ? 's' : ''}</strong>
            </p>
          </div>

          {/* Seat Map Legend */}
          <div className="mb-10 flex flex-wrap justify-center gap-6">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-500">
              <span className="size-4 rounded border-2 border-[#102a56] bg-[#102a56]" /> BUSINESS
            </div>
            <div className="flex items-center gap-2 text-xs font-bold text-slate-500">
              <span className="size-4 rounded border-2 border-blue-100 bg-white" /> ECONOMY
            </div>
            <div className="flex items-center gap-2 text-xs font-bold text-slate-500">
              <span className="size-4 rounded border-2 border-[#f59e0b] bg-[#f59e0b]" /> SELECTED
            </div>
            <div className="flex items-center gap-2 text-xs font-bold text-slate-500">
              <span className="size-4 rounded border-2 border-slate-100 bg-slate-100" /> UNAVAILABLE
            </div>
          </div>

          {loading ? (
            <div className="py-20 text-center text-slate-400 font-medium flex flex-col items-center">
              <span className="size-8 border-4 border-slate-200 border-t-[#2563eb] rounded-full animate-spin mb-4" />
              Loading seat map...
            </div>
          ) : (
            <div className="flex justify-center w-full">
              <div className="w-full max-w-[480px] rounded-t-[8rem] rounded-b-[4rem] border-[3px] border-slate-200 bg-slate-50 px-3 py-10 sm:px-8 relative overflow-hidden shadow-[inset_0_0_40px_rgba(0,0,0,0.02)]">
                {/* Nose detail */}
                <div className="absolute top-0 left-1/2 -translate-x-1/2 w-32 h-8 bg-slate-200/50 rounded-b-3xl" />
                
                <div className="mb-10 mt-2 text-center text-[10px] font-bold uppercase tracking-[.25em] text-slate-400">
                  Cockpit
                </div>

                <p className="mb-4 text-center text-sm text-slate-500">Your search is for {cabinClass === 'BUSINESS' ? 'Business' : 'Economy'}. Seats in the other cabin cannot be selected. To change cabin, start a new search.</p>
              {/* Rows */}
                <div className="flex flex-col gap-3 relative z-10">
                  {currentSeatMap.map(([row, rowSeats], index) => {
                    const isBusiness = rowSeats.some(s => s.cabinClass === 'BUSINESS')
                    const isFirstBusiness = isBusiness && (index === 0 || !currentSeatMap[index - 1][1].some(s => s.cabinClass === 'BUSINESS'))
                    const isFirstEconomy = !isBusiness && (index === 0 || currentSeatMap[index - 1][1].some(s => s.cabinClass === 'BUSINESS'))
                    
                    const leftSeats = rowSeats.slice(0, rowSeats.length / 2)
                    const rightSeats = rowSeats.slice(rowSeats.length / 2)
                    
                    return (
                      <div key={row} className="flex flex-col gap-2">
                        {isFirstBusiness && (
                          <div className="flex items-center gap-3 my-2">
                            <div className="h-px bg-slate-200 flex-1" />
                            <span className="text-[10px] font-bold uppercase tracking-widest text-[#102a56] bg-blue-50 px-2 py-0.5 rounded border border-blue-100">
                              Business Class
                            </span>
                            <div className="h-px bg-slate-200 flex-1" />
                          </div>
                        )}
                        {isFirstEconomy && (
                          <div className="flex items-center gap-3 my-2">
                            <div className="h-px bg-slate-200 flex-1" />
                            <span className="text-[10px] font-bold uppercase tracking-widest text-slate-400 bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
                              Economy Class
                            </span>
                            <div className="h-px bg-slate-200 flex-1" />
                          </div>
                        )}

                        <div className="flex items-center justify-between gap-1 sm:gap-2">
                          {/* Left Seats */}
                          <div className="flex gap-1 sm:gap-2">
                            {leftSeats.map(seat => {
                              const isSelected = currentSeats.some(s => s?.id === seat.id)
                              const isSelectedByActive = currentSeats[activePaxIndex]?.id === seat.id
                              const isAvailable = seat.status === 'AVAILABLE' && seat.cabinClass === cabinClass && !isSelected

                              return (
                                <button
                                  key={seat.id}
                                  type="button"
                                  title={seat.cabinClass !== cabinClass ? 'Different cabin from your search' : seat.status !== 'AVAILABLE' && !isSelected ? 'Seat unavailable' : `Select seat ${seat.seatNo}`}
                                  disabled={!isAvailable && !isSelected}
                                  onClick={() => toggleSeat(seat)}
                                  className={`size-9 sm:size-10 rounded-lg sm:rounded-xl flex flex-col items-center justify-center font-bold text-xs transition-all cursor-pointer relative ${
                                    isSelected
                                      ? 'bg-[#f59e0b] text-[#102a56] ring-2 ring-[#f59e0b] shadow-md shadow-amber-500/20 z-20'
                                      : isAvailable
                                        ? seat.cabinClass === 'BUSINESS'
                                          ? 'bg-[#102a56] text-white hover:bg-[#1a3d75] shadow-xs'
                                          : 'bg-white border-2 border-slate-200 text-slate-700 hover:border-slate-400 shadow-xs'
                                        : 'bg-slate-200 text-slate-400 border border-slate-300/40 cursor-not-allowed opacity-60'
                                  }`}
                                >
                                  <span className="text-[11px] leading-none">{seat.seatNo}</span>
                                  {isSelectedByActive && (
                                    <span className="absolute -top-1 -right-1 size-3 bg-[#2563eb] rounded-full border-2 border-white ring-1 ring-blue-500" />
                                  )}
                                </button>
                              )
                            })}
                          </div>

                          {/* Aisle */}
                          <div className="size-6 sm:size-8 flex items-center justify-center text-[10px] font-bold text-slate-400 font-mono">
                            {row}
                          </div>

                          {/* Right Seats */}
                          <div className="flex gap-1 sm:gap-2">
                            {rightSeats.map(seat => {
                              const isSelected = currentSeats.some(s => s?.id === seat.id)
                              const isSelectedByActive = currentSeats[activePaxIndex]?.id === seat.id
                              const isAvailable = seat.status === 'AVAILABLE' && seat.cabinClass === cabinClass && !isSelected

                              return (
                                <button
                                  key={seat.id}
                                  type="button"
                                  title={seat.cabinClass !== cabinClass ? 'Different cabin from your search' : seat.status !== 'AVAILABLE' && !isSelected ? 'Seat unavailable' : `Select seat ${seat.seatNo}`}
                                  disabled={!isAvailable && !isSelected}
                                  onClick={() => toggleSeat(seat)}
                                  className={`size-9 sm:size-10 rounded-lg sm:rounded-xl flex flex-col items-center justify-center font-bold text-xs transition-all cursor-pointer relative ${
                                    isSelected
                                      ? 'bg-[#f59e0b] text-[#102a56] ring-2 ring-[#f59e0b] shadow-md shadow-amber-500/20 z-20'
                                      : isAvailable
                                        ? seat.cabinClass === 'BUSINESS'
                                          ? 'bg-[#102a56] text-white hover:bg-[#1a3d75] shadow-xs'
                                          : 'bg-white border-2 border-slate-200 text-slate-700 hover:border-slate-400 shadow-xs'
                                        : 'bg-slate-200 text-slate-400 border border-slate-300/40 cursor-not-allowed opacity-60'
                                  }`}
                                >
                                  <span className="text-[11px] leading-none">{seat.seatNo}</span>
                                  {isSelectedByActive && (
                                    <span className="absolute -top-1 -right-1 size-3 bg-[#2563eb] rounded-full border-2 border-white ring-1 ring-blue-500" />
                                  )}
                                </button>
                              )
                            })}
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
                
                <div className="mt-12 mb-4 text-center text-[10px] font-bold uppercase tracking-[.25em] text-slate-400">
                  Rear Galley
                </div>
                
                {/* Wing representations */}
                <div className="absolute top-[35%] -left-6 w-12 h-64 bg-slate-200/40 rounded-r-[100%] skew-y-12" />
                <div className="absolute top-[35%] -right-6 w-12 h-64 bg-slate-200/40 rounded-l-[100%] -skew-y-12" />
              </div>
            </div>
          )}
        </section>

        {/* Booking Summary Sidebar (Sticky) */}
        <aside className="h-fit rounded-3xl border border-slate-200 bg-white shadow-sm sticky top-[140px] overflow-hidden">
          <div className="bg-slate-50 p-6 border-b border-slate-100">
            <h2 className="mt-1 text-2xl font-bold text-[#102a56]">Assign {cabinClass.toLowerCase()} seats</h2>
          </div>
          
          <div className="p-4">
            <div className="flex flex-col gap-2 min-h-[120px] max-h-[360px] overflow-y-auto pr-2 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:bg-slate-200 [&::-webkit-scrollbar-thumb]:rounded-full hover:[&::-webkit-scrollbar-thumb]:bg-slate-300 transition-colors">
              {eligiblePax.map((pax, index) => {
                const seat = currentSeats[index]
                const otherLegSeat = activeLeg === 'outbound' ? returnSeats[index] : outboundSeats[index]
                const isActive = activePaxIndex === index
                
                return (
                  <button 
                    key={index} 
                    onClick={() => setActivePaxIndex(index)}
                    className={`flex items-start justify-between rounded-xl p-4 border text-left transition-all cursor-pointer ${
                      isActive 
                        ? 'border-[#2563eb] bg-blue-50 ring-1 ring-[#2563eb]' 
                        : seat 
                          ? 'border-emerald-200 bg-emerald-50/50 hover:bg-emerald-50' 
                          : 'border-slate-200 bg-white hover:border-slate-300'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className={`flex size-8 items-center justify-center rounded-full text-xs font-bold ${
                        isActive ? 'bg-[#2563eb] text-white' : seat ? 'bg-emerald-500 text-white' : 'bg-slate-200 text-slate-500'
                      }`}>
                        {index + 1}
                      </div>
                      <div>
                        <p className={`text-xs font-bold ${isActive ? 'text-[#2563eb]' : 'text-slate-500 uppercase'}`}>{pax.title}</p>
                        <p className="font-bold text-slate-800 text-sm truncate max-w-[120px]">{pax.name || 'Passenger'}</p>
                        {returnFlight && (
                          <p className="text-[10px] text-slate-400 mt-0.5">
                            {activeLeg === 'outbound' 
                              ? `Return: ${otherLegSeat ? otherLegSeat.seatNo : 'None'}` 
                              : `Outbound: ${otherLegSeat ? otherLegSeat.seatNo : 'None'}`}
                          </p>
                        )}
                      </div>
                    </div>
                    
                    <div className="flex flex-col items-end">
                      {seat ? (
                        <>
                          <div className="bg-[#102a56] text-white px-2.5 py-0.5 rounded-full text-[10px] font-bold mb-1">
                            Seat {seat.seatNo}
                          </div>
                          <strong className="text-xs text-slate-500">
                            {(() => {
                              const base = activeLeg === 'return' ? returnBasePrice : outboundBasePrice
                              const fee = Math.max(0, seat.price - base)
                              return fee === 0 ? 'Included' : `+${formatVND(fee)}`
                            })()}
                          </strong>
                        </>
                      ) : (
                        <span className={`text-xs font-bold ${isActive ? 'text-[#2563eb]' : 'text-slate-400'}`}>
                          Selecting...
                        </span>
                      )}
                    </div>
                  </button>
                )
              })}
            </div>

            <div className="mt-6 pt-5 border-t border-slate-200">
              <div className="flex items-center justify-between mb-2">
                <span className="font-bold text-slate-600">Estimated total (with seats)</span>
                <strong className="text-2xl font-bold text-[#f59e0b] tabular-nums">{formatVND(finalTotal)}</strong>
              </div>
              {seatLoadError && <p role="alert" className="text-sm text-red-700">Could not load seats. <button onClick={() => setSeatReload(n => n + 1)} className="underline">Retry</button></p>}
              {holdError && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-600 rounded-xl text-sm font-medium">
                  {holdError}
                </div>
              )}
              {!isAllSeatsSelected && <p className="mt-3 text-sm text-slate-500">{needsReturn ? 'Outbound seats selected. Select seats for the return flight to continue.' : 'Select a seat for each passenger on each flight. Auto-assign is optional.'}</p>}
              {!isAllSeatsSelected && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleAutoAssign}
                  className="w-full mt-4 rounded-xl border-dashed border-slate-300 py-5 text-xs font-bold text-slate-700 hover:bg-slate-50 cursor-pointer flex items-center justify-center gap-2"
                >
                  <Armchair className="size-4 text-[#f59e0b]" />
                  Auto-assign Seats (Lowest Available Fare)
                </Button>
              )}
              <Button 
                disabled={(!isAllSeatsSelected && !needsReturn) || holding || loading || seatLoadError}
                onClick={() => { if (needsReturn) { setActiveLeg('return'); setActivePaxIndex(returnSeats.findIndex(s => !s)); } else void handleContinue() }}
                className="w-full mt-3 rounded-xl bg-[#f59e0b] py-6 text-base font-bold text-[#102a56] shadow-md shadow-amber-500/20 hover:bg-[#d97706] disabled:opacity-50 disabled:shadow-none cursor-pointer"
              >
                {holding ? 'Holding Seats...' : needsReturn ? 'Choose return seats' : 'Continue to Payment'}
                <ArrowRight className="size-5 ml-2" />
              </Button>
            </div>
          </div>
        </aside>

      </div>
    </main>
  )
}
