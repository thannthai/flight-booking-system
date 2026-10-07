'use client'

import { useState, useEffect } from 'react'
import { Plane, X, Check, ArrowRight, ArrowLeft, User, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatVND } from '@/lib/format'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { UserNav } from '@/components/auth/UserNav'
import type { FlightSeatMapResponse, FlightSeatResponse } from '@/types/api'

type AdaptedFlight = {
  id: number;
  airline: string;
  code: string;
  logo: string;
  tone: string;
  depart: string;
  arrive: string;
  duration: string;
  price: string;
  aircraft: string;
  badge: string;
}

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

export function SeatSelection({ 
  flight, 
  returnFlight,
  passengerData,
  bookingData,
  initialSeats = [],
  initialReturnSeats = [],
  historicallySelectedSeats = [],
  onSeatSelect,
  onBack, 
  onHome, 
  onContinue 
}: { 
  flight: AdaptedFlight | null; 
  returnFlight?: AdaptedFlight | null;
  passengerData: any;
  bookingData?: any;
  initialSeats?: (FlightSeatResponse | null)[];
  initialReturnSeats?: (FlightSeatResponse | null)[];
  historicallySelectedSeats?: number[];
  onSeatSelect?: (seatId: number) => void;
  onBack: () => void; 
  onHome: () => void; 
  onContinue: (seats: FlightSeatResponse[], booking: any, returnSeats?: FlightSeatResponse[]) => void;
}) {
  const { isAuthenticated, openAuthModal } = useAuth()
  
  if (!flight || !passengerData || !passengerData.paxList) {
    return (
      <div className="min-h-screen bg-[#f7f8fa] flex flex-col items-center justify-center p-5">
        <div className="bg-white p-8 rounded-3xl shadow-sm border border-slate-200 text-center max-w-md">
          <Plane className="size-12 text-slate-300 mx-auto mb-4" />
          <h2 className="text-xl font-bold text-slate-800 mb-2">Session Expired</h2>
          <p className="text-slate-500 text-sm mb-6">Your booking session has expired or is invalid. Please start a new search.</p>
          <Button onClick={onHome} className="w-full bg-[#102a56] hover:bg-[#1a3d75] rounded-xl h-12 font-bold">
            Start New Search
          </Button>
        </div>
      </div>
    )
  }

  // Filter out infants because they don't get seats
  const eligiblePax = passengerData.paxList
    .map((p: any, i: number) => ({ ...p, name: passengerData.passengers[i].name, originalIndex: i }))
    .filter((p: any) => p.type !== 'Infant')

  // Dual-leg selection state
  const [activeLeg, setActiveLeg] = useState<'outbound' | 'return'>('outbound')
  const [outboundSeats, setOutboundSeats] = useState<(FlightSeatResponse | null)[]>(
    initialSeats.length > 0 ? initialSeats : new Array(eligiblePax.length).fill(null)
  )
  const [returnSeats, setReturnSeats] = useState<(FlightSeatResponse | null)[]>(
    initialReturnSeats.length > 0 ? initialReturnSeats : new Array(eligiblePax.length).fill(null)
  )
  const [activePaxIndex, setActivePaxIndex] = useState(0)

  const [outboundSeatMap, setOutboundSeatMap] = useState<[number, FlightSeatResponse[]][]>([])
  const [returnSeatMap, setReturnSeatMap] = useState<[number, FlightSeatResponse[]][]>([])
  const [loading, setLoading] = useState(true)
  const [holding, setHolding] = useState(false)
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
  useEffect(() => {
    setLoading(true)
    const fetchPromises = [
      api.get<FlightSeatMapResponse>(`/flights/${flight.id}/seats`).then(res => {
        setOutboundSeatMap(parseSeatMap(res.seats))
      })
    ]

    if (returnFlight) {
      fetchPromises.push(
        api.get<FlightSeatMapResponse>(`/flights/${returnFlight.id}/seats`).then(res => {
          setReturnSeatMap(parseSeatMap(res.seats))
        })
      )
    }

    Promise.all(fetchPromises)
      .catch(err => {
        console.error('Failed to load seats', err)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [flight.id, returnFlight?.id])

  const toggleSeat = (seat: FlightSeatResponse) => {
    const isAlreadySelected = currentSeats.some(s => s?.id === seat.id)
    const activeInitialSeats = activeLeg === 'return' ? initialReturnSeats : initialSeats
    const isHeldByMe = historicallySelectedSeats.includes(seat.id) || activeInitialSeats.some(s => s?.id === seat.id)
    
    if (!isAlreadySelected && !isHeldByMe && seat.status !== 'AVAILABLE') return
    
    if (onSeatSelect) {
      onSeatSelect(seat.id)
    }
    
    setCurrentSeats(current => {
      const newSeats = [...current]
      const existingOwnerIndex = newSeats.findIndex(s => s?.id === seat.id)
      if (existingOwnerIndex !== -1) {
        newSeats[existingOwnerIndex] = null
        return newSeats
      }
      newSeats[activePaxIndex] = seat
      return newSeats
    })

    // Auto-advance to next unassigned passenger
    setTimeout(() => {
      setCurrentSeats(latestSeats => {
        const nextUnassigned = latestSeats.findIndex(s => s === null)
        if (nextUnassigned !== -1) {
          setActivePaxIndex(nextUnassigned)
        }
        return latestSeats
      })
    }, 50)
  }

  const isAllSeatsSelected = outboundSeats.every(s => s !== null) && (!returnFlight || returnSeats.every(s => s !== null))

  const handleAutoAssign = () => {
    const assignAvailable = (map: [number, FlightSeatResponse[]][], currentList: (FlightSeatResponse | null)[]) => {
      const available: FlightSeatResponse[] = []
      map.forEach(([_, rowSeats]) => {
        rowSeats.forEach(s => {
          const isAssigned = currentList.some(sel => sel?.id === s.id)
          const isCandidate = (s.status === 'AVAILABLE' || historicallySelectedSeats.includes(s.id)) && !isAssigned
          if (isCandidate) available.push(s)
        })
      })
      const res = [...currentList]
      let idx = 0
      for (let i = 0; i < eligiblePax.length; i++) {
        if (!res[i] && idx < available.length) {
          res[i] = available[idx++]
          if (onSeatSelect) onSeatSelect(res[i]!.id)
        }
      }
      return res
    }

    const newOutbound = assignAvailable(outboundSeatMap, outboundSeats)
    setOutboundSeats(newOutbound)

    if (returnFlight) {
      const newReturn = assignAvailable(returnSeatMap, returnSeats)
      setReturnSeats(newReturn)
    }
  }
  
  const handleContinue = async () => {
    if (!isAuthenticated) {
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

    setHolding(true)
    setHoldError(null)
    
    try {
      const seatIds = [
        ...outboundSeats.map(s => s!.id),
        ...(returnFlight ? returnSeats.map(s => s!.id) : [])
      ]
      await api.post<any>('/bookings/hold-seats', { seatIds })
      
      onContinue(outboundSeats as FlightSeatResponse[], null, returnFlight ? returnSeats as FlightSeatResponse[] : undefined)
    } catch (err: any) {
      setHolding(false)
      const msg = err.message || ''
      const isAuthIssue = 
        err.status === 401 || 
        err.errorCode === 'UNAUTHORIZED' || 
        msg.toLowerCase().includes('jwt') || 
        msg.toLowerCase().includes('unauthorized') ||
        msg.toLowerCase().includes('expired') ||
        msg.toLowerCase().includes('session')

      if (isAuthIssue) {
        setHoldError(null)
        openAuthModal('login', () => {
          handleContinue()
        })
        return
      }

      setHoldError(err.message || 'Failed to hold seats. Some seats may have just been reserved.')
    }
  }

  // Price calculation
  const outboundBasePrice = Number(flight.price.replace(/\D/g, ''))
  const outboundSeatPrice = outboundSeats.reduce((sum, s) => {
    if (!s) return sum;
    return sum + Math.max(0, s.price - outboundBasePrice);
  }, 0)

  const returnBasePrice = returnFlight ? Number(returnFlight.price.replace(/\D/g, '')) : 0
  const returnSeatPrice = returnFlight ? returnSeats.reduce((sum, s) => {
    if (!s) return sum;
    return sum + Math.max(0, s.price - returnBasePrice);
  }, 0) : 0

  const totalSeatPrice = outboundSeatPrice + returnSeatPrice

  const finalTotal = (passengerData.pricing?.totalBaseFare || 0) + 
                     (passengerData.pricing?.totalTaxes || 0) + 
                     (passengerData.pricing?.addOnsTotal || 0) - 
                     (passengerData.pricing?.discount || 0) + 
                     totalSeatPrice

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
                <img src={`/airlines/${flight.logo}.svg`} alt={flight.airline} className="h-8 w-12 object-contain bg-white rounded-lg p-1" />
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
                              const isHeldByMe = historicallySelectedSeats.includes(seat.id) || 
                                (activeLeg === 'return' ? initialReturnSeats : initialSeats).some(s => s?.id === seat.id)
                              const isAvailable = (seat.status === 'AVAILABLE' || isHeldByMe) && !isSelected

                              return (
                                <button
                                  key={seat.id}
                                  type="button"
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
                              const isHeldByMe = historicallySelectedSeats.includes(seat.id) || 
                                (activeLeg === 'return' ? initialReturnSeats : initialSeats).some(s => s?.id === seat.id)
                              const isAvailable = (seat.status === 'AVAILABLE' || isHeldByMe) && !isSelected

                              return (
                                <button
                                  key={seat.id}
                                  type="button"
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
            <p className="text-[10px] font-bold uppercase tracking-[.2em] text-slate-400">Seat assignments</p>
            <h2 className="mt-1 text-2xl font-bold text-[#102a56]">Assign seats</h2>
          </div>
          
          <div className="p-4">
            <div className="flex flex-col gap-2 min-h-[120px] max-h-[360px] overflow-y-auto pr-2 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:bg-slate-200 [&::-webkit-scrollbar-thumb]:rounded-full hover:[&::-webkit-scrollbar-thumb]:bg-slate-300 transition-colors">
              {eligiblePax.map((pax: any, index: number) => {
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
                <span className="font-bold text-slate-600">Total (with seats)</span>
                <strong className="text-2xl font-bold text-[#f59e0b] tabular-nums">{formatVND(finalTotal)}</strong>
              </div>
              {holdError && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-600 rounded-xl text-sm font-medium">
                  {holdError}
                </div>
              )}
              {!isAllSeatsSelected && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleAutoAssign}
                  className="w-full mt-4 rounded-xl border-dashed border-slate-300 py-5 text-xs font-bold text-slate-700 hover:bg-slate-50 cursor-pointer flex items-center justify-center gap-2"
                >
                  <Sparkles className="size-4 text-[#f59e0b]" />
                  Auto-assign Seats (Free)
                </Button>
              )}
              <Button 
                disabled={!isAllSeatsSelected || holding} 
                onClick={handleContinue} 
                className="w-full mt-3 rounded-xl bg-[#f59e0b] py-6 text-base font-bold text-[#102a56] shadow-md shadow-amber-500/20 hover:bg-[#d97706] disabled:opacity-50 disabled:shadow-none cursor-pointer"
              >
                {holding ? 'Holding Seats...' : 'Continue to Payment'}
                <ArrowRight className="size-5 ml-2" />
              </Button>
            </div>
          </div>
        </aside>

      </div>
    </main>
  )
}
