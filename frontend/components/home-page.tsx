'use client'

import { useEffect, useState, useMemo } from 'react'
import { ArrowRight, CalendarDays, MapPin, Plane, Plus, Search, Users, Minus, Check } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import type { AirportResponse } from '@/types/api'
import { UserNav } from '@/components/auth/UserNav'

export interface SearchParams {
  originCode: string;
  destinationCode: string;
  departureDate: string;
  returnDate: string | null;
  passengers: { adults: number; children: number; infants: number };
  cabinClass: 'ECONOMY' | 'BUSINESS';
}

export function HomePage({ onSearch }: { onSearch: (params: SearchParams) => void }) {
  const [trip, setTrip] = useState<'Round trip' | 'One way'>('Round trip')
  const [passengersOpen, setPassengersOpen] = useState(false)
  const [adults, setAdults] = useState(1)
  const [children, setChildren] = useState(0)
  const [infants, setInfants] = useState(0)
  const [cabinClass, setCabinClass] = useState<'ECONOMY' | 'BUSINESS'>('ECONOMY')
  
  const todayStr = useMemo(() => {
    const now = new Date()
    const year = now.getFullYear()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }, [])

  const [departureDate, setDepartureDate] = useState(() => {
    const now = new Date()
    const year = now.getFullYear()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  })

  const [returnDate, setReturnDate] = useState(() => {
    const d = new Date()
    d.setDate(d.getDate() + 3)
    const year = d.getFullYear()
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  })

  const [airports, setAirports] = useState<AirportResponse[]>([])
  const [from, setFrom] = useState<AirportResponse | null>(null)
  const [to, setTo] = useState<AirportResponse | null>(null)

  useEffect(() => {
    api.get<{ content: AirportResponse[] }>('/airports?size=100')
      .then(res => {
        const data = res.content || []
        setAirports(data)
        if (data.length > 0) {
          setFrom(data.find(a => a.iataCode === 'SGN') || data[0] || null)
          setTo(data.find(a => a.iataCode === 'HAN') || data[1] || null)
        }
      })
      .catch(err => console.error('Failed to load airports', err))
  }, [])

  const total = adults + children

  const handleSearch = () => {
    const safeDep = departureDate < todayStr ? todayStr : departureDate
    const safeRet = (trip === 'Round trip' && returnDate < safeDep) ? safeDep : returnDate
    onSearch({
      originCode: from?.iataCode || 'SGN',
      destinationCode: to?.iataCode || 'HAN',
      departureDate: safeDep,
      returnDate: trip === 'Round trip' ? safeRet : null,
      passengers: { adults, children, infants },
      cabinClass
    })
  }

  const handleDestinationClick = (code: string) => {
    const apt = airports.find(a => a.iataCode === code)
    if (apt) setTo(apt)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950">
      {/* Header */}
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-20 max-w-7xl items-center justify-between px-5 lg:px-8">
          <button onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })} className="flex items-center gap-3 cursor-pointer" aria-label="Skyway home">
            <span className="flex size-10 items-center justify-center rounded-xl bg-[#102a56] text-white shadow-sm"><Plane className="size-5 -rotate-12" /></span>
            <span className="text-lg font-semibold tracking-tight">skyway<span className="text-[#f59e0b]">.</span></span>
          </button>
          
          <nav className="hidden items-center text-sm font-medium text-slate-500 md:flex">
            <UserNav />
          </nav>
        </div>
      </header>

      {/* Hero Section */}
      <section className="mx-auto grid max-w-7xl gap-10 px-5 pb-8 pt-10 lg:grid-cols-[1.1fr_.9fr] lg:items-center lg:px-8 lg:pt-12">
        <div>
          <p className="mb-4 text-sm font-semibold uppercase tracking-[.2em] text-[#2563eb]">Travel further, feel closer</p>
          <h1 className="max-w-2xl text-5xl font-semibold tracking-[-.04em] text-[#102a56] sm:text-6xl">
            The world is <span className="text-amber-500">closer than ever.</span>
          </h1>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-slate-500">
            Find the right flight, at the right fare, with a booking experience designed around you.
          </p>
        </div>
        <div className="relative hidden h-[300px] overflow-hidden rounded-[2rem] shadow-xl lg:block">
          <img src="https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=1200&q=90" alt="Airplane wing above the clouds" className="size-full object-cover" />
          <div className="absolute inset-0 bg-gradient-to-t from-slate-950/75 via-transparent to-transparent" />
          <div className="absolute bottom-8 left-8 text-white">
            <p className="text-sm uppercase tracking-[.2em] text-white/80">Fly beyond</p>
            <p className="mt-2 text-3xl font-semibold">One flight at a time.</p>
          </div>
        </div>
      </section>

      {/* Flight Search Section */}
      <section className="relative z-10 mx-auto max-w-7xl px-5 lg:px-8 mb-20 -mt-2">
        <div className="rounded-3xl border border-slate-200/90 bg-white p-5 shadow-[0_8px_30px_rgb(0,0,0,0.06)] sm:p-7">
          
          {/* Trip Type Segmented Control */}
          <div className="mb-5 flex flex-wrap items-center gap-4">
            <div className="inline-flex rounded-full bg-slate-100 p-1 border border-slate-200/80">
              {(['Round trip', 'One way'] as const).map((item) => (
                <button 
                  key={item} 
                  type="button"
                  onClick={() => setTrip(item)} 
                  className={`cursor-pointer rounded-full px-4 py-1.5 text-xs font-semibold transition-all ${
                    trip === item 
                      ? 'bg-[#102a56] text-white shadow-xs' 
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {item}
                </button>
              ))}
            </div>
          </div>

          {/* Search Inputs Grid */}
          <div className="grid gap-3 lg:grid-cols-[1fr_auto_1fr_1fr_1fr_1.2fr] lg:items-center">
            
            {/* Origin Airport */}
            <AirportSelector label="FROM" value={from} onChange={setFrom} airports={airports} />
            
            {/* Swap Button */}
            <button 
              type="button" 
              aria-label="Swap departure and destination" 
              onClick={() => { const temp = from; setFrom(to); setTo(temp) }} 
              className="flex size-10 shrink-0 self-center items-center justify-center rounded-full border border-slate-200 bg-white text-base text-[#2563eb] shadow-2xs transition-all hover:border-[#2563eb] hover:bg-blue-50 cursor-pointer"
            >
              ⇄
            </button>
            
            {/* Destination Airport */}
            <AirportSelector label="TO" value={to} onChange={setTo} airports={airports} />
            
            {/* Departure Date */}
            <div className="group relative h-[68px] rounded-2xl border border-slate-200 bg-white p-3 flex flex-col justify-center transition-all hover:border-slate-300 focus-within:border-[#2563eb] focus-within:ring-2 focus-within:ring-blue-100">
              <span className="block text-[10px] font-semibold tracking-[.16em] text-slate-400 transition-colors group-focus-within:text-[#2563eb]">DEPARTURE</span>
              <div className="mt-0.5 flex items-center gap-2">
                <CalendarDays className="size-4 shrink-0 text-slate-400 transition-colors group-focus-within:text-[#2563eb]" />
                <input 
                  type="date" 
                  value={departureDate} 
                  min={todayStr}
                  onChange={e => {
                    const val = e.target.value
                    setDepartureDate(val)
                    if (returnDate && returnDate < val) {
                      setReturnDate(val)
                    }
                  }}
                  className="w-full bg-transparent text-sm font-semibold text-slate-800 outline-none cursor-pointer"
                />
              </div>
            </div>

            {/* Return Date */}
            <div className={`group relative h-[68px] rounded-2xl border border-slate-200 p-3 flex flex-col justify-center transition-all ${
              trip === 'One way' 
                ? 'pointer-events-none opacity-40 bg-slate-50' 
                : 'bg-white hover:border-slate-300 focus-within:border-[#2563eb] focus-within:ring-2 focus-within:ring-blue-100'
            }`}>
              <span className="block text-[10px] font-semibold tracking-[.16em] text-slate-400 transition-colors group-focus-within:text-[#2563eb]">RETURN</span>
              <div className="mt-0.5 flex items-center gap-2">
                <CalendarDays className="size-4 shrink-0 text-slate-400 transition-colors group-focus-within:text-[#2563eb]" />
                <input 
                  type="date" 
                  disabled={trip === 'One way'}
                  value={returnDate} 
                  min={departureDate || todayStr}
                  onChange={e => setReturnDate(e.target.value)}
                  className="w-full bg-transparent text-sm font-semibold text-slate-800 outline-none cursor-pointer disabled:cursor-not-allowed"
                />
              </div>
            </div>
            
            {/* Passengers & Cabin */}
            <div className="relative">
              <button 
                type="button"
                onClick={() => setPassengersOpen(!passengersOpen)} 
                className="group h-[68px] w-full rounded-2xl border border-slate-200 bg-white p-3 text-left transition-all hover:border-slate-300 focus:outline-none focus:border-[#2563eb] focus:ring-2 focus:ring-blue-100 cursor-pointer flex flex-col justify-center"
              >
                <span className="block text-[10px] font-semibold tracking-[.16em] text-slate-400 uppercase transition-colors group-focus:text-[#2563eb]">Passengers</span>
                <span className="mt-0.5 flex items-center gap-1.5 text-sm font-semibold text-slate-800 truncate">
                  <Users className="size-4 shrink-0 text-slate-400 transition-colors group-focus:text-[#2563eb]" />
                  <span className="truncate">{total + infants} {total + infants === 1 ? 'Passenger' : 'Passengers'} · {cabinClass === 'ECONOMY' ? 'Economy' : 'Business'}</span>
                </span>
              </button>
              
              {passengersOpen && (
                <>
                  {/* Backdrop for closing popover on click outside */}
                  <div className="fixed inset-0 z-40" onClick={() => setPassengersOpen(false)} />
                  <div className="absolute right-0 top-full z-50 mt-2 w-80 rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl">
                    <Stepper 
                      label="Adults" 
                      detail="Age 12+" 
                      value={adults} 
                      min={Math.max(1, infants)} 
                      max={9 - children} 
                      onChange={(value) => { setAdults(value); setInfants((current) => Math.min(current, value)) }} 
                    />
                    <Stepper 
                      label="Children" 
                      detail="Age 2–11" 
                      value={children} 
                      min={0} 
                      max={9 - adults} 
                      onChange={setChildren} 
                    />
                    <Stepper 
                      label="Infants" 
                      detail="Under 2 years · 1 per adult (on lap)" 
                      value={infants} 
                      min={0} 
                      max={adults} 
                      onChange={setInfants} 
                    />
                    
                    <div className="mt-4 pt-4 text-center text-[10px] font-medium text-amber-600 border-t border-slate-100">
                      Maximum 9 passengers (excluding lap infants). 1 infant per adult.
                    </div>
                    
                    <div className="my-4 border-t border-slate-100 pt-4">
                      <p className="mb-3 text-sm font-semibold text-slate-800">Cabin Class</p>
                      <div className="flex rounded-xl bg-slate-100 p-1">
                        {(['ECONOMY', 'BUSINESS'] as const).map(c => (
                          <button
                            key={c}
                            type="button"
                            onClick={() => setCabinClass(c)}
                            className={`flex-1 rounded-lg py-2 text-xs font-semibold transition-all cursor-pointer ${
                              cabinClass === c ? 'bg-[#102a56] text-white shadow-xs' : 'text-slate-600 hover:text-slate-900'
                            }`}
                          >
                            {c === 'ECONOMY' ? 'Economy' : 'Business'}
                          </button>
                        ))}
                      </div>
                    </div>

                    <button 
                      type="button"
                      onClick={() => setPassengersOpen(false)} 
                      className="mt-2 w-full cursor-pointer rounded-xl bg-[#f59e0b] py-2.5 font-semibold text-[#102a56] hover:bg-[#d97706] transition-colors"
                    >
                      Apply
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>

          <div className="mt-5 flex items-center justify-end border-t border-slate-100 pt-5">
            <Button 
              type="button"
              onClick={handleSearch} 
              className="h-12 w-full sm:w-auto rounded-full bg-[#f59e0b] px-8 text-base font-bold text-[#102a56] shadow-sm transition-all hover:bg-[#d97706] hover:shadow cursor-pointer flex items-center justify-center gap-2"
            >
              <Search className="size-5" />
              <span>Search flights</span>
            </Button>
          </div>
        </div>
      </section>

    </main>
  )
}

function Stepper({ label, detail, value, min, max, onChange }: { label: string; detail: string; value: number; min: number; max?: number; onChange: (value: number) => void }) { 
  return (
    <div className="flex items-center justify-between border-b border-slate-100 py-3 last:border-0">
      <div>
        <p className="text-sm font-semibold text-slate-800">{label}</p>
        <p className="text-xs text-slate-400">{detail}</p>
      </div>
      <div className="flex items-center gap-3">
        <button type="button" disabled={value <= min} onClick={() => onChange(value - 1)} className="flex size-8 items-center justify-center rounded-full border border-slate-200 disabled:opacity-40 cursor-pointer">
          <Minus className="size-4" />
        </button>
        <span className="w-4 text-center font-semibold">{value}</span>
        <button type="button" disabled={max !== undefined && value >= max} onClick={() => onChange(value + 1)} className="flex size-8 items-center justify-center rounded-full border border-slate-200 disabled:opacity-40 cursor-pointer">
          <Plus className="size-4" />
        </button>
      </div>
    </div>
  )
}

function AirportSelector({ 
  label, 
  value, 
  onChange, 
  airports 
}: { 
  label: string; 
  value: AirportResponse | null; 
  onChange: (airport: AirportResponse) => void; 
  airports: AirportResponse[];
}) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  
  const filtered = airports.filter(a => 
    a.name.toLowerCase().includes(search.toLowerCase()) || 
    a.city.toLowerCase().includes(search.toLowerCase()) || 
    a.iataCode.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="relative">
      <button 
        type="button"
        onClick={() => setOpen(!open)}
        className="group h-[68px] w-full rounded-2xl border border-slate-200 bg-white p-3 text-left transition-all hover:border-slate-300 focus:outline-none focus:border-[#2563eb] focus:ring-2 focus:ring-blue-100 cursor-pointer flex flex-col justify-center"
      >
        <span className="block text-[10px] font-semibold tracking-[.16em] text-slate-400 uppercase transition-colors group-focus:text-[#2563eb]">{label}</span>
        <span className="mt-0.5 flex items-center gap-1.5 text-sm font-semibold text-slate-800 truncate">
          <MapPin className="size-4 shrink-0 text-slate-400 transition-colors group-focus:text-[#2563eb]" />
          <span className="truncate">{value ? `${value.city} (${value.iataCode})` : 'Select airport'}</span>
        </span>
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute top-full left-0 z-50 mt-2 w-72 rounded-2xl border border-slate-200 bg-white shadow-2xl">
            <div className="border-b border-slate-100 p-3">
              <input 
                autoFocus
                className="w-full rounded-xl bg-slate-50 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-[#2563eb]" 
                placeholder="Search city or airport code..." 
                value={search}
                onChange={e => setSearch(e.target.value)}
              />
            </div>
            <div className="max-h-60 overflow-y-auto p-2">
              {filtered.length > 0 ? filtered.map(airport => (
                <button
                  key={airport.id}
                  type="button"
                  onClick={() => { onChange(airport); setOpen(false); setSearch('') }}
                  className="flex w-full items-center justify-between rounded-xl px-3 py-2 text-left hover:bg-slate-50 cursor-pointer"
                >
                  <div className="overflow-hidden">
                    <div className="font-semibold text-[#102a56]">{airport.city} ({airport.iataCode})</div>
                    <div className="truncate text-xs text-slate-500">{airport.name}</div>
                  </div>
                  {value?.id === airport.id && <Check className="size-4 shrink-0 text-[#f59e0b]" />}
                </button>
              )) : (
                <div className="p-3 text-center text-sm text-slate-500">No airports found</div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
