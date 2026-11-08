'use client'

import Image from 'next/image'

import { SkywayBrand } from '@/components/SkywayBrand'

import { useEffect, useState, useMemo, useRef } from 'react'
import { ArrowRight, Check, Filter, Luggage, Plane, PlaneTakeoff, PlaneLanding, Utensils, X, ChevronLeft, ChevronRight, SlidersHorizontal } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { api } from '@/lib/api'
import { fetchAllPages } from '@/lib/search'
import { formatVND, formatTime, formatDate, airportDate, airportHour, computeDuration } from '@/lib/format'
import type { FlightResponse } from '@/types/api'
import type { SearchParams } from '@/lib/checkout-model'
import { UserNav } from '@/components/auth/UserNav'

interface FlightResultsProps {
  searchParams: SearchParams;
  onModify: () => void;
  onSelectFlight: (outbound: FlightResponse, returnFlight?: FlightResponse) => void;
}

function FilterSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="border-b border-slate-100 py-5 first:pt-0">
      <h3 className="mb-4 text-sm font-semibold text-slate-900">{title}</h3>
      <div className="flex flex-col gap-3">{children}</div>
    </div>
  )
}

function FlightCard({
  flight,
  onSelect
}: {
  flight: FlightResponse;
  onSelect: () => void;
}) {
  const [showDetails, setShowDetails] = useState(false)
  const [imgError, setImgError] = useState(false)
  
  const duration = computeDuration(flight.departureTime, flight.arrivalTime)
  const isVietnamAirlines = flight.airline.code === 'VN'
  const isVietJet = flight.airline.code === 'VJ'
  const toneBg = isVietnamAirlines ? 'bg-sky-900' : isVietJet ? 'bg-red-600' : 'bg-emerald-700'

  return (
    <Card className="border-slate-200/90 shadow-xs transition-all hover:border-slate-300 hover:shadow-md">
      <CardContent className="p-5 sm:p-6">
        {/* Top: Airline & Flight Info */}
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            {!imgError ? (
              <Image unoptimized width={64} height={40}
                src={`/airlines/${flight.airline.code}.svg`} 
                alt={flight.airline.name} 
                className="h-10 w-16 object-contain rounded-lg border border-slate-100 p-1 bg-white"
                onError={() => setImgError(true)}
              />
            ) : (
              <span className={`flex size-10 items-center justify-center rounded-xl ${toneBg} text-xs font-bold text-white shadow-xs`}>
                {flight.airline.code}
              </span>
            )}
            <div>
              <p className="font-semibold text-slate-900">{flight.airline.name}</p>
              <p className="mt-0.5 text-xs text-slate-400">
                {flight.flightNo} · {flight.aircraft.model}
              </p>
            </div>
          </div>
          <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 border border-emerald-100">
            {flight.source === 'DEMO' ? 'Simulated schedule' : 'Non-stop'}
          </span>
        </div>

        {/* Middle: Depart - Duration - Arrive */}
        <div className="my-6 grid items-center gap-4 sm:grid-cols-[1fr_auto_1fr] sm:gap-6">
          <div>
            <p className="text-2xl font-bold tracking-tight text-slate-900 tabular-nums">
              {formatTime(flight.departureTime, flight.origin.timezone)}
            </p>
            <p className="text-xs font-semibold text-[#2563eb]">
              {formatDate(flight.departureTime, 'en-GB', flight.origin.timezone)}
            </p>
            <p className="mt-1 text-sm font-medium text-slate-500">
              {flight.origin.iataCode} · {flight.origin.city}
            </p>
          </div>

          <div className="flex items-center gap-2 text-center text-xs text-slate-400 sm:flex-col sm:gap-1">
            <span className="flex items-center gap-2 text-slate-300">
              <PlaneTakeoff className="size-4" />
              <span className="h-[2px] w-8 border-t-2 border-dashed border-slate-200 sm:w-20" />
              <Plane className="size-4 text-[#f59e0b]" />
              <span className="h-[2px] w-8 border-t-2 border-dashed border-slate-200 sm:w-20" />
              <PlaneLanding className="size-4" />
            </span>
            <span className="font-medium text-slate-500 mt-1">{duration}</span>
          </div>

          <div className="sm:text-right">
            <p className="text-2xl font-bold tracking-tight text-slate-900 tabular-nums">
              {formatTime(flight.arrivalTime, flight.dest.timezone)}
            </p>
            <p className="text-xs font-semibold text-slate-400">
              {formatDate(flight.arrivalTime, 'en-GB', flight.dest.timezone)}
            </p>
            <p className="mt-1 text-sm font-medium text-slate-500">
              {flight.dest.iataCode} · {flight.dest.city}
            </p>
          </div>
        </div>

        {/* Bottom: Amenities & Price / Action */}
        <div className="flex flex-wrap items-end justify-between gap-4 border-t border-slate-100 pt-4">
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap gap-2 text-xs text-slate-500">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-50 px-3 py-1.5 border border-slate-100">
                <Luggage className="size-3.5 text-slate-600" />
                {flight.airline.freeBaggage ? '20kg checked bag' : '7kg carry-on'}
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-50 px-3 py-1.5 border border-slate-100">
                <Utensils className="size-3.5 text-slate-600" />
                Meals available as add-ons
              </span>
            </div>
            <button 
              onClick={() => setShowDetails(!showDetails)}
              className="text-xs font-semibold text-[#2563eb] hover:text-blue-800 flex items-center gap-1.5 w-max cursor-pointer hover:underline underline-offset-2"
            >
              Flight details
            </button>
          </div>

          <div className="flex w-full items-center justify-between gap-5 sm:w-auto sm:justify-end">
            <div className="text-right">
              <p className="text-xl font-bold text-[#102a56] tabular-nums">
                {formatVND(flight.basePrice)}
              </p>
              <p className="text-[11px] text-slate-400">/ adult base fare · tax & extras added later</p>
            </div>
            <Button
              type="button"
              onClick={onSelect}
              className="rounded-xl px-5 font-semibold shadow-sm transition-all cursor-pointer bg-[#f59e0b] hover:bg-[#d97706] text-[#102a56]"
            >
              Select flight
              <ArrowRight className="size-4 ml-1.5" />
            </Button>
          </div>
        </div>

        {/* Drawer / Accordion Mock */}
        {showDetails && (
          <div className="fixed inset-0 z-50 flex justify-end">
            {/* Backdrop */}
            <div 
              className="absolute inset-0 bg-slate-950/20 backdrop-blur-sm animate-in fade-in duration-200"
              onClick={() => setShowDetails(false)}
            />
            {/* Panel */}
            <div className="relative w-full max-w-md bg-white shadow-2xl animate-in slide-in-from-right duration-300 h-full overflow-y-auto flex flex-col text-left">
              <div className="flex items-center justify-between p-5 border-b border-slate-100">
                <div>
                  <h3 className="text-lg font-bold text-slate-900">Flight details</h3>
                  <p className="text-xs text-slate-500 mt-0.5">{flight.origin.city} to {flight.dest.city}</p>
                </div>
                <button 
                  onClick={() => setShowDetails(false)}
                  className="rounded-full p-2 hover:bg-slate-100 text-slate-500 transition-colors cursor-pointer"
                >
                  <X className="size-5" />
                </button>
              </div>
              <div className="p-5 flex-1">
                {/* Visual Timeline */}
                <div className="relative pl-6 border-l-2 border-dashed border-slate-200 ml-4 mb-8">
                  <div className="absolute -left-[11px] top-0 size-5 rounded-full bg-slate-100 border-2 border-white flex items-center justify-center">
                    <div className="size-2 rounded-full bg-[#2563eb]" />
                  </div>
                  <div className="mb-6">
                    <p className="text-xl font-bold text-slate-900 tabular-nums">{formatTime(flight.departureTime, flight.origin.timezone)}</p>
                    <p className="font-semibold text-slate-700 mt-1">{flight.origin.city} ({flight.origin.iataCode})</p>
                  </div>
                  
                  <div className="absolute -left-[17px] top-1/2 -translate-y-1/2 flex items-center justify-center bg-white py-2">
                    <Plane className="size-5 text-slate-400 rotate-180" />
                  </div>
                  
                  <div className="absolute -left-[11px] bottom-0 size-5 rounded-full bg-slate-100 border-2 border-white flex items-center justify-center">
                    <div className="size-2 rounded-full bg-[#f59e0b]" />
                  </div>
                  <div className="">
                    <p className="text-xl font-bold text-slate-900 tabular-nums">{formatTime(flight.arrivalTime, flight.dest.timezone)}</p>
                    <p className="font-semibold text-slate-700 mt-1">{flight.dest.city} ({flight.dest.iataCode})</p>
                  </div>
                </div>

                {/* Details Grid */}
                <div className="grid gap-4 bg-slate-50 p-5 rounded-2xl border border-slate-100">
                  <div>
                    <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-3">Baggage</h4>
                    <ul className="space-y-2.5 text-sm text-slate-700">
                      <li className="flex items-start gap-3"><Check className="size-4 text-emerald-500 mt-0.5" /> Cabin baggage 7kg</li>
                      <li className="flex items-start gap-3"><Check className="size-4 text-emerald-500 mt-0.5" /> Checked baggage {flight.airline.freeBaggage ? '20kg' : 'Not included'}</li>
                    </ul>
                  </div>
                  <div className="h-px w-full bg-slate-200 my-2" />
                  <div>
                    <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-3">Amenities</h4>
                    <ul className="space-y-2.5 text-sm text-slate-700">
                      <li className="flex items-start gap-3"><Check className="size-4 text-emerald-500 mt-0.5" /> {flight.aircraft.model}</li>
                      <li className="flex items-start gap-3"><Check className="size-4 text-emerald-500 mt-0.5" /> Standard legroom (29&quot;)</li>
                      <li className="flex items-start gap-3"><Check className="size-4 text-emerald-500 mt-0.5" /> In-flight meal available</li>
                    </ul>
                  </div>
                </div>
              </div>
              <div className="p-5 border-t border-slate-100 bg-white">
                <Button
                  type="button"
                  onClick={() => { setShowDetails(false); onSelect(); }}
                  className="w-full rounded-xl py-6 font-bold shadow-sm bg-[#f59e0b] hover:bg-[#d97706] text-[#102a56] cursor-pointer"
                >
                  Select flight
                  <ArrowRight className="size-5 ml-2" />
                </Button>
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export function FlightResults({ searchParams, onModify, onSelectFlight }: FlightResultsProps) {
  const [mobileFilters, setMobileFilters] = useState(false)
  const [sort, setSort] = useState<'Cheapest' | 'Earliest' | 'Fastest'>('Cheapest')

  const [flights, setFlights] = useState<FlightResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Filters state
  const [selectedAirlines, setSelectedAirlines] = useState<string[]>([])
  const [maxPrice, setMaxPrice] = useState<number | null>(null)
  const [selectedTimes, setSelectedTimes] = useState<string[]>([]) // Morning, Afternoon, Evening

  // Round-trip state
  const [selectedOutbound, setSelectedOutbound] = useState<FlightResponse | null>(null)
  const isReturnLeg = Boolean(searchParams.returnDate && selectedOutbound)
  const currentOrigin = isReturnLeg ? (searchParams.destinationCode || 'HAN') : (searchParams.originCode || 'SGN')
  const currentDest = isReturnLeg ? (searchParams.originCode || 'SGN') : (searchParams.destinationCode || 'HAN')

  // Carousel state
  const [activeDate, setActiveDate] = useState(searchParams.departureDate)
  const carouselRef = useRef<HTMLDivElement>(null)

  // Minimum allowed date boundary
  const minDateStr = useMemo(() => {
    const now = new Date()
    const today = airportDate(now.toISOString(), isReturnLeg ? searchParams.destinationTimezone : searchParams.originTimezone)
    if (isReturnLeg && selectedOutbound) {

      const outStr = airportDate(selectedOutbound.departureTime, selectedOutbound.dest.timezone)
      return outStr > today ? outStr : today
    }
    return today
  }, [isReturnLeg, selectedOutbound, searchParams.originTimezone, searchParams.destinationTimezone])

  // Generate future days starting from minDateStr (never in the past)
  const baseDateStr = isReturnLeg ? (searchParams.returnDate || searchParams.departureDate) : searchParams.departureDate
  const dateCarousel = useMemo(() => {
    const dates = []
    const base = new Date(baseDateStr + 'T12:00:00Z')
    const minD = new Date(minDateStr + 'T12:00:00Z')
    
    // Start up to 2 days before base, but never before minD
    const candidateStart = new Date(base)
    candidateStart.setUTCDate(candidateStart.getUTCDate() - 2)
    const startDate = candidateStart < minD ? minD : candidateStart

    for (let i = 0; i < 14; i++) {
      const d = new Date(startDate)
      d.setUTCDate(startDate.getUTCDate() + i)
      const dateString = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
      if (dateString < minDateStr) continue
      
      const weekday = d.toLocaleDateString('en-US', { weekday: 'short', timeZone: 'UTC' })
      const day = d.toLocaleDateString('en-US', { day: '2-digit', timeZone: 'UTC' })
      const month = d.toLocaleDateString('en-US', { month: 'short', timeZone: 'UTC' }).substring(0, 3).toUpperCase()
      const formatted = `${weekday}, ${day} ${month}`
      dates.push({ date: dateString, label: formatted })
    }
    return dates
  }, [baseDateStr, minDateStr])

  const scrollCarousel = (direction: 'left' | 'right') => {
    if (carouselRef.current) {
      const scrollAmount = 300 // Scroll by ~300px
      carouselRef.current.scrollBy({ left: direction === 'left' ? -scrollAmount : scrollAmount, behavior: 'smooth' })
    }
  }

  // Scroll to the beginning on mount / leg switch
  useEffect(() => {
    if (carouselRef.current) {
      carouselRef.current.scrollLeft = 0
    }
  }, [isReturnLeg])

  const [retrySearch, setRetrySearch] = useState(0)
  const queryKey = JSON.stringify([currentOrigin, currentDest, activeDate, retrySearch])
  const [previousQuery, setPreviousQuery] = useState(queryKey)
  if (previousQuery !== queryKey) {
    setPreviousQuery(queryKey)
    setLoading(true)
    setFlights([])
    setSelectionError(null)
    setError(null)
  }

  // Fetch flights from real backend API
  useEffect(() => {
    let active = true
    const origin = currentOrigin
    const dest = currentDest
    const date = activeDate

    // Call Spring Boot search endpoint
    const query = `/flights/search?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(dest)}${date ? `&departureDate=${date}` : ''}`

    fetchAllPages<FlightResponse>(query, api.get)
      .then(data => { if (active) setFlights(data) })
      .catch(() => { if (active) setError('Could not load flights. Please try again.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [currentOrigin, currentDest, activeDate, retrySearch])

  // Fetch all airlines globally
  const [allAirlines, setAllAirlines] = useState<[string, string][]>([])
  useEffect(() => {
    const fetchAirlines = async () => {
      try {
        const airlines = await fetchAllPages<{name: string; code: string}>('/airlines', api.get)
        setAllAirlines(airlines.map(a => [a.name, a.code]))
      } catch {
        setAllAirlines([])

      }
    }
    fetchAirlines()
  }, [])

  // Filter and sort flights
  const filteredFlights = useMemo(() => {
    return flights
      .filter(flight => {
        if (selectedAirlines.length > 0 && !selectedAirlines.includes(flight.airline.name)) {
          return false
        }
        if (maxPrice !== null && flight.basePrice > maxPrice) {
          return false
        }
        if (selectedTimes.length > 0) {
          const hour = airportHour(flight.departureTime, flight.origin.timezone)
          let timeOfDay = ''
          if (hour >= 0 && hour < 12) timeOfDay = 'Morning'
          else if (hour >= 12 && hour < 18) timeOfDay = 'Afternoon'
          else timeOfDay = 'Evening'
          
          if (!selectedTimes.includes(timeOfDay)) {
            return false
          }
        }
        return true
      })
      .sort((a, b) => {
        if (sort === 'Cheapest') return a.basePrice - b.basePrice
        if (sort === 'Earliest') return new Date(a.departureTime).getTime() - new Date(b.departureTime).getTime()
        if (sort === 'Fastest') {
          const durationA = new Date(a.arrivalTime).getTime() - new Date(a.departureTime).getTime()
          const durationB = new Date(b.arrivalTime).getTime() - new Date(b.departureTime).getTime()
          return durationA - durationB
        }
        return 0
      })
  }, [flights, selectedAirlines, maxPrice, selectedTimes, sort])

  const toggleAirlineFilter = (name: string) => {
    setSelectedAirlines(prev => 
      prev.includes(name) ? prev.filter(a => a !== name) : [...prev, name]
    )
  }

  const toggleTimeFilter = (time: string) => {
    setSelectedTimes(prev => 
      prev.includes(time) ? prev.filter(t => t !== time) : [...prev, time]
    )
  }

  const priceCeiling = Math.ceil(Math.max(1000000, ...flights.map(f => f.basePrice)) / 100000) * 100000

  const totalPax = searchParams.passengers.adults + searchParams.passengers.children + searchParams.passengers.infants

  const handleFlightCardSelect = (flight: FlightResponse) => {
    if (searchParams.returnDate && !selectedOutbound) {
      setSelectedOutbound(flight)

      const outStr = airportDate(flight.departureTime, flight.dest.timezone)
      const returnDate = (searchParams.returnDate && searchParams.returnDate >= outStr) ? searchParams.returnDate : outStr
      setActiveDate(returnDate)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } else if (searchParams.returnDate && selectedOutbound) {
      if (Date.parse(flight.departureTime) <= Date.parse(selectedOutbound.departureTime)) { setSelectionError('Return departure must be after outbound departure. Choose another flight or date.'); return }
      onSelectFlight(selectedOutbound, flight)
    } else {
      onSelectFlight(flight)
    }
  }
  
  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950 pb-20">
      {/* Header */}
      <header className="bg-white sticky top-0 z-40">
        <div className="border-b border-slate-200">
          <div className="mx-auto flex h-[72px] max-w-7xl items-center justify-between px-5 lg:px-8">
            <button onClick={onModify} className="flex items-center gap-3 cursor-pointer" aria-label="Skyway home">
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
            <button onClick={onModify} className="flex items-center gap-2 text-emerald-600 font-bold hover:underline cursor-pointer">
              <Check className="size-4" /> Search
            </button>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2 text-[#102a56] font-bold">
              <div className="size-5 rounded-full bg-[#102a56] text-white flex items-center justify-center text-[10px] shadow-[0_0_0_3px_rgba(16,42,86,0.1)]">2</div> Flights
            </span>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2"><div className="size-5 rounded-full border border-slate-300 flex items-center justify-center text-[10px]">3</div> Passengers</span>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2"><div className="size-5 rounded-full border border-slate-300 flex items-center justify-center text-[10px]">4</div> Seats</span>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2"><div className="size-5 rounded-full border border-slate-300 flex items-center justify-center text-[10px]">5</div> Payment</span>
          </div>
        </div>
      </header>

      {selectionError && <p role="alert" className="p-4 text-red-700">{selectionError}</p>}
      {/* Date Carousel & Search Summary */}
      <section className="bg-[#102a56] text-white py-6">
        <div className="mx-auto max-w-7xl px-5 lg:px-8">
          {/* Summary Row */}
          <div className="flex items-center justify-between gap-5 mb-4">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
              <span className="text-xl font-bold">{currentOrigin}</span>
              <ArrowRight className="size-4 text-[#fbbf24]" />
              <span className="text-xl font-bold">{currentDest}</span>
              <span className="ml-2 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-slate-200 shadow-sm border border-white/5">
                {totalPax} {totalPax === 1 ? 'Passenger' : 'Passengers'} · {searchParams.cabinClass === 'ECONOMY' ? 'Economy' : 'Business'}
              </span>
              {searchParams.returnDate && (
                <span className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold uppercase tracking-wider ${
                  isReturnLeg ? 'bg-amber-400 text-slate-950' : 'bg-blue-500/20 text-blue-200 border border-blue-400/30'
                }`}>
                  {isReturnLeg ? 'Step 2: Return Flight' : 'Step 1: Outbound Flight'}
                </span>
              )}
            </div>
            <Button 
              onClick={onModify} 
              variant="outline" 
              className="rounded-full border-white/30 bg-white/10 text-white hover:bg-white/20 hover:text-white cursor-pointer h-9 px-4 text-xs font-semibold shrink-0"
            >
              <SlidersHorizontal className="size-3.5 mr-1.5" />
              Modify Search
            </Button>
          </div>

          {/* Outbound Selected Summary (in Round-trip) */}
          {selectedOutbound && (
            <div className="mb-4 rounded-2xl bg-white/10 border border-white/10 p-3.5 flex flex-wrap items-center justify-between gap-3 text-xs">
              <div className="flex items-center gap-3">
                <span className="size-7 rounded-lg bg-emerald-500 text-white flex items-center justify-center font-bold">✓</span>
                <div>
                  <span className="text-emerald-400 font-bold block uppercase text-[10px] tracking-wider">Outbound Selected</span>
                  <span className="font-semibold text-white">
                    {selectedOutbound.airline.name} ({selectedOutbound.flightNo}) · {formatTime(selectedOutbound.departureTime, selectedOutbound.origin.timezone)} - {formatTime(selectedOutbound.arrivalTime, selectedOutbound.dest.timezone)}
                  </span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  setSelectedOutbound(null)
                  setActiveDate(searchParams.departureDate)
                }}
                className="font-bold text-amber-300 hover:text-amber-200 underline cursor-pointer"
              >
                Change Outbound Flight
              </button>
            </div>
          )}

          {/* Carousel */}
          <div className="relative rounded-2xl bg-white/10 p-1 mt-4">
            <div className="flex items-center justify-between">
              <button 
                onClick={() => scrollCarousel('left')}
                className="flex size-10 shrink-0 items-center justify-center text-white hover:bg-white/10 rounded-xl transition-colors cursor-pointer" 
                aria-label="Scroll left"
              >
                <ChevronLeft className="size-5" />
              </button>
              
              <div ref={carouselRef} className="flex flex-1 overflow-x-auto no-scrollbar scroll-smooth">
                <div className="flex w-max justify-start gap-1 px-1">
                  {dateCarousel.map((d) => (
                    <button
                      key={d.date}
                      onClick={() => setActiveDate(d.date)}
                      className={`flex items-center justify-center rounded-xl py-2.5 px-4 w-[125px] transition-all cursor-pointer shrink-0 ${
                        activeDate === d.date 
                          ? 'bg-white text-[#102a56] shadow-sm font-bold scale-105 z-10' 
                          : 'text-slate-300 hover:bg-white/10 hover:text-white font-medium'
                      }`}
                    >
                      <span className="text-xs tracking-wide">{d.label}</span>
                    </button>
                  ))}
                </div>
              </div>

              <button 
                onClick={() => scrollCarousel('right')}
                className="flex size-10 shrink-0 items-center justify-center text-white hover:bg-white/10 rounded-xl transition-colors cursor-pointer" 
                aria-label="Scroll right"
              >
                <ChevronRight className="size-5" />
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* Main Content */}
      <div className="mx-auto max-w-7xl px-5 py-7 lg:px-8">
        <div className="mb-5 flex items-center justify-between lg:hidden">
          <p className="font-semibold text-slate-800">{filteredFlights.length} flights</p>
          <Button variant="outline" onClick={() => setMobileFilters(true)} className="rounded-full cursor-pointer">
            <Filter className="size-4 mr-1.5" /> Filters
          </Button>
        </div>

        <div className="grid gap-7 lg:grid-cols-[260px_1fr]">
          {/* Filters Sidebar */}
          <aside className={`${mobileFilters ? 'fixed inset-0 z-50 flex' : 'hidden'} bg-slate-950/40 lg:static lg:block lg:bg-transparent`}>
            <div className="ml-auto h-full w-[min(88vw,340px)] overflow-y-auto bg-white p-6 shadow-2xl lg:h-auto lg:w-auto lg:rounded-2xl lg:border lg:border-slate-200 lg:p-5 lg:shadow-xs">
              <div className="flex items-center justify-between lg:mb-5">
                <h2 className="text-base font-semibold text-slate-900">Filters</h2>
                <button className="rounded-full p-2 hover:bg-slate-100 lg:hidden cursor-pointer" onClick={() => setMobileFilters(false)} aria-label="Close filters">
                  <X className="size-5" />
                </button>
              </div>

              {/* Airlines filter */}
              <FilterSection title="Airlines">
                {allAirlines.length > 0 ? allAirlines.map(([name, code]) => (
                  <label key={code} className="flex cursor-pointer items-center gap-3 text-sm text-slate-700 hover:bg-slate-50 p-2 rounded-lg -mx-2">
                    <input 
                      type="checkbox" 
                      checked={selectedAirlines.includes(name)}
                      onChange={() => toggleAirlineFilter(name)}
                      className="size-4 rounded border-slate-300 accent-[#102a56] cursor-pointer" 
                    />
                    <span className="font-medium">{name}</span>
                  </label>
                )) : (
                  <p className="text-xs text-slate-400">Airline filters unavailable</p>
                )}
              </FilterSection>

              {/* Departure Time */}
              <FilterSection title="Departure Time">
                <div className="flex flex-col gap-2">
                  {[
                    { id: 'Morning', label: 'Morning', time: '00:00 - 11:59' },
                    { id: 'Afternoon', label: 'Afternoon', time: '12:00 - 17:59' },
                    { id: 'Evening', label: 'Evening', time: '18:00 - 23:59' }
                  ].map(t => (
                    <label key={t.id} className="flex cursor-pointer items-center justify-between text-sm text-slate-700 hover:bg-slate-50 p-2 rounded-lg -mx-2">
                      <div className="flex items-center gap-3">
                        <input 
                          type="checkbox" 
                          checked={selectedTimes.includes(t.id)}
                          onChange={() => toggleTimeFilter(t.id)}
                          className="size-4 rounded border-slate-300 accent-[#102a56] cursor-pointer" 
                        />
                        <span className="font-medium">{t.label}</span>
                      </div>
                      <span className="text-[10px] text-slate-400">{t.time}</span>
                    </label>
                  ))}
                </div>
              </FilterSection>

              {/* Price range */}
              <FilterSection title="Max Price">
                <div className="flex items-center justify-between text-xs font-semibold text-slate-700 tabular-nums">
                  <span>0 ₫</span>
                  <span className="text-[#102a56]">{maxPrice === null ? 'No limit' : formatVND(maxPrice)}</span>
                </div>
                <input 
                  type="range" 
                  min="0"
                  max={priceCeiling}
                  step="100000"
                  value={maxPrice ?? priceCeiling}
                  onChange={e => setMaxPrice(Number(e.target.value))}
                  className="mt-3 w-full accent-[#f59e0b] cursor-pointer" 
                />
                <button onClick={() => { setMaxPrice(null); setSelectedAirlines([]); setSelectedTimes([]) }} className="text-xs text-blue-700 underline">Reset filters</button>
              </FilterSection>

              <Button onClick={() => setMobileFilters(false)} className="mt-4 w-full rounded-xl bg-[#f59e0b] text-[#102a56] font-bold hover:bg-[#d97706] lg:hidden cursor-pointer">
                Apply Filters
              </Button>
            </div>
          </aside>

          {/* Results List */}
          <section>
            <div className="mb-6 flex flex-col sm:flex-row sm:items-end justify-between gap-4">
              <div>
                <h1 className="text-2xl font-bold tracking-tight text-slate-900">
                  Select your flight
                </h1>
                <p className="mt-1 text-sm text-slate-500">
                  Found <strong className="text-slate-900">{filteredFlights.length}</strong> flights on {activeDate}
                </p>
              </div>
              
              {/* Quick Sort Pills */}
              <div className="flex items-center gap-2 p-1 bg-white rounded-full border border-slate-200 shadow-2xs overflow-x-auto no-scrollbar self-start sm:self-auto">
                {(['Cheapest', 'Earliest', 'Fastest'] as const).map(s => (
                  <button
                    key={s}
                    onClick={() => setSort(s)}
                    className={`rounded-full px-4 py-1.5 text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
                      sort === s 
                        ? 'bg-slate-900 text-white shadow-xs' 
                        : 'text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    {s === 'Cheapest' && 'Lowest Price'}
                    {s === 'Earliest' && 'Earliest Flight'}
                    {s === 'Fastest' && 'Fastest Duration'}
                  </button>
                ))}
              </div>
            </div>

            {/* Loading State */}
            {loading && (
              <div className="flex flex-col gap-4">
                {[1, 2, 3].map(i => (
                  <div key={i} className="animate-pulse rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
                    <div className="flex justify-between items-center mb-6">
                      <div className="h-6 w-36 rounded bg-slate-200" />
                      <div className="h-6 w-24 rounded bg-slate-200" />
                    </div>
                    <div className="flex justify-between items-center mb-6">
                      <div className="h-8 w-20 rounded bg-slate-200" />
                      <div className="h-4 w-32 rounded bg-slate-100" />
                      <div className="h-8 w-20 rounded bg-slate-200" />
                    </div>
                    <div className="h-10 w-full rounded bg-slate-100" />
                  </div>
                ))}
              </div>
            )}

            {/* Error State */}
            {!loading && error && (
              <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-center text-red-700">
                <p className="font-semibold">{error}</p>
                <Button onClick={() => setRetrySearch(n => n + 1)} variant="outline" className="mt-3 rounded-full cursor-pointer">
                  Retry search
                </Button>
              </div>
            )}

            {/* Empty State */}
            {!loading && !error && filteredFlights.length === 0 && (
              <div className="rounded-3xl border border-slate-200 bg-white p-12 text-center shadow-xs">
                <div className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-amber-50 text-amber-600">
                  <Plane className="size-7" />
                </div>
                <h3 className="mt-4 text-lg font-bold text-slate-900">No flights found</h3>
                <p className="mt-2 text-sm text-slate-500 max-w-md mx-auto">
                  We couldn&apos;t find any flights matching your filters on {activeDate}. Try changing the date or adjusting your filters.
                </p>
              </div>
            )}

            {/* Flight Cards List */}
            {!loading && !error && filteredFlights.length > 0 && (
              <div className="flex flex-col gap-4">
                {filteredFlights.map((flight) => (
                  <FlightCard
                    key={flight.id}
                    flight={flight}
                    onSelect={() => handleFlightCardSelect(flight)}
                  />
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </main>
  )
}
