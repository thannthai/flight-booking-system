'use client'

import { useState, useEffect, useRef } from 'react'
import { HomePage, type SearchParams } from '@/components/home-page'
import { FlightResults } from '@/components/flights/FlightResults'
import { SeatSelection } from '@/components/seats/SeatSelection'
import { PassengerDetails } from '@/components/passenger/PassengerDetails'
import { VietQRPayment } from '@/components/payment/VietQRPayment'
import { formatTime, formatVND } from '@/lib/format'
import type { FlightResponse, FlightSeatResponse } from '@/types/api'
import { api } from '@/lib/api'

export interface AdaptedFlight {
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
  departDate?: string;
}

const getTodayStr = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function adaptFlight(flight: FlightResponse): AdaptedFlight {
  return {
    id: flight.id,
    airline: flight.airline.name,
    code: flight.flightNo,
    logo: flight.airline.code,
    tone: flight.airline.code === 'VN' ? 'bg-sky-900' : flight.airline.code === 'VJ' ? 'bg-red-600' : 'bg-emerald-700',
    depart: formatTime(flight.departureTime),
    arrive: formatTime(flight.arrivalTime),
    duration: '2h 15m',
    price: formatVND(flight.basePrice),
    aircraft: flight.aircraft.model,
    badge: '',
    departDate: new Date(flight.departureTime).toLocaleDateString('en-GB', { weekday: 'short', day: '2-digit', month: 'short' })
  }
}

export default function BookingApp() {
  const [view, setView] = useState<'home' | 'results' | 'seats' | 'passengers' | 'payment'>('home')
  const [searchParams, setSearchParams] = useState<SearchParams>(() => ({
    originCode: 'SGN',
    destinationCode: 'HAN',
    departureDate: getTodayStr(),
    returnDate: null,
    passengers: { adults: 1, children: 0, infants: 0 },
    cabinClass: 'ECONOMY'
  }))
  const [selectedSeats, setSelectedSeats] = useState<FlightSeatResponse[]>([])
  const [selectedReturnSeats, setSelectedReturnSeats] = useState<FlightSeatResponse[]>([])
  const [historicallySelectedSeats, setHistoricallySelectedSeats] = useState<number[]>([])
  const [passengerData, setPassengerData] = useState<any>(null)
  const [bookingData, setBookingData] = useState<any>(null)
  const [selectedFlight, setSelectedFlight] = useState<AdaptedFlight | null>(null)
  const [selectedReturnFlight, setSelectedReturnFlight] = useState<AdaptedFlight | null>(null)

  const handleHome = async () => {
    if (bookingData?.id) {
      try {
        await api.post(`/bookings/${bookingData.id}/cancel`, {})
      } catch (e) {}
    }
    setBookingData(null)
    setSelectedFlight(null)
    setSelectedReturnFlight(null)
    setSelectedSeats([])
    setSelectedReturnSeats([])
    setPassengerData(null)
    setHistoricallySelectedSeats([])
    navigateTo('home')
  }

  const navigateTo = (newView: 'home' | 'results' | 'seats' | 'passengers' | 'payment', pushHistory = true) => {
    setView(newView)
    window.scrollTo({ top: 0, behavior: 'instant' })
    if (typeof window !== 'undefined' && pushHistory) {
      const url = new URL(window.location.href)
      if (newView === 'home') {
        url.searchParams.delete('step')
      } else {
        url.searchParams.set('step', newView)
      }
      window.history.pushState({ step: newView }, '', url.toString())
    }
  }

  const bookingDataRef = useRef(bookingData)
  useEffect(() => {
    bookingDataRef.current = bookingData
  }, [bookingData])

  // Handle browser Back / Forward buttons (popstate)
  useEffect(() => {
    const handlePopState = (e: PopStateEvent) => {
      if (bookingDataRef.current?.id) {
        window.history.pushState({ step: 'payment' }, '', '?step=payment')
        setView('payment')
        return
      }
      const step = e.state?.step || (new URL(window.location.href).searchParams.get('step') as any) || 'home'
      setView(step)
      window.scrollTo({ top: 0, behavior: 'instant' })
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  // Restore session on reload (F5) or initial load
  useEffect(() => {
    try {
      const urlStep = new URL(window.location.href).searchParams.get('step') as any
      const saved = sessionStorage.getItem('skyway_session')
      if (saved) {
        const parsed = JSON.parse(saved)
        if (parsed.selectedFlight) setSelectedFlight(parsed.selectedFlight)
        if (parsed.selectedReturnFlight) setSelectedReturnFlight(parsed.selectedReturnFlight)
        if (parsed.searchParams) setSearchParams(parsed.searchParams)
        if (parsed.passengerData) setPassengerData(parsed.passengerData)
        if (parsed.selectedSeats) setSelectedSeats(parsed.selectedSeats)
        if (parsed.selectedReturnSeats) setSelectedReturnSeats(parsed.selectedReturnSeats)
        if (parsed.historicallySelectedSeats) setHistoricallySelectedSeats(parsed.historicallySelectedSeats)
        if (parsed.bookingData) setBookingData(parsed.bookingData)
        
        if (parsed.bookingData?.id) {
          setView('payment')
          if (typeof window !== 'undefined') window.history.replaceState({ step: 'payment' }, '', '?step=payment')
        } else if (urlStep) {
          setView(urlStep)
        } else if (parsed.view && parsed.view !== 'home') {
          setView(parsed.view)
        }
      } else if (urlStep) {
        setView(urlStep)
      }
    } catch (e) {
      console.warn('Failed to restore session:', e)
    }
  }, [])

  // Persist session across navigation
  useEffect(() => {
    try {
      if (view === 'home') {
        sessionStorage.removeItem('skyway_session')
      } else {
        sessionStorage.setItem('skyway_session', JSON.stringify({
          view,
          selectedFlight,
          selectedReturnFlight,
          searchParams,
          passengerData,
          selectedSeats,
          selectedReturnSeats,
          historicallySelectedSeats,
          bookingData
        }))
      }
    } catch (e) {
      // Ignore quota errors in private browsing
    }
  }, [view, selectedFlight, selectedReturnFlight, searchParams, passengerData, selectedSeats, selectedReturnSeats, bookingData])

  const handleFlightSelect = async (outbound: FlightResponse, returnFlight?: FlightResponse) => {
    if (bookingData?.id) {
      try { await api.post(`/bookings/${bookingData.id}/cancel`, {}) } catch (e) {}
      setBookingData(null)
    }
    setSelectedFlight(adaptFlight(outbound))
    if (returnFlight) {
      setSelectedReturnFlight(adaptFlight(returnFlight))
    } else {
      setSelectedReturnFlight(null)
    }
    setSelectedSeats([])
    setSelectedReturnSeats([])
    navigateTo('passengers')
  }

  return view === 'home' ? (
    <HomePage 
      onSearch={(params) => {
        setSearchParams(params)
        navigateTo('results')
      }} 
    />
  ) : view === 'results' ? (
    <FlightResults 
      searchParams={searchParams}
      onModify={handleHome} 
      onSelectFlight={handleFlightSelect} 
    />
  ) : view === 'passengers' ? (
    <PassengerDetails 
      flight={selectedFlight}
      returnFlight={selectedReturnFlight}
      searchParams={searchParams}
      passengerData={passengerData}
      onBack={() => navigateTo('results')} 
      onHome={handleHome} 
      onContinue={async (data) => { 
        if (bookingData?.id) {
          try { await api.post(`/bookings/${bookingData.id}/cancel`, {}) } catch (e) {}
          setBookingData(null)
        }
        setPassengerData(data); 
        navigateTo('seats') 
      }} 
    />
  ) : view === 'seats' ? (
    <SeatSelection 
      flight={selectedFlight} 
      returnFlight={selectedReturnFlight}
      passengerData={passengerData}
      bookingData={bookingData}
      initialSeats={selectedSeats}
      initialReturnSeats={selectedReturnSeats}
      historicallySelectedSeats={historicallySelectedSeats}
      onSeatSelect={(seatId) => setHistoricallySelectedSeats(prev => Array.from(new Set([...prev, seatId])))}
      onBack={() => navigateTo('passengers')} 
      onHome={handleHome} 
      onContinue={(seats, booking, returnSeats) => { 
        setSelectedSeats(seats); 
        setSelectedReturnSeats(returnSeats || []);
        setBookingData(booking); 
        navigateTo('payment'); 
      }} 
    />
  ) : (
    <VietQRPayment 
      flight={selectedFlight} 
      returnFlight={selectedReturnFlight}
      seats={selectedSeats} 
      returnSeats={selectedReturnSeats}
      passengerData={passengerData}
      bookingData={bookingData}
      onBookingCreated={(booking) => setBookingData(booking)}
      onBack={() => navigateTo('seats')} 
      onHome={handleHome} 
    />
  )
}
