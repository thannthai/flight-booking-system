'use client'

import { useState, useEffect, useRef, useCallback, useLayoutEffect } from 'react'
import { HomePage } from '@/components/home-page'
import type { SearchParams } from '@/lib/checkout-model'
import { FlightResults } from '@/components/flights/FlightResults'
import { SeatSelection } from '@/components/seats/SeatSelection'
import { PassengerDetails } from '@/components/passenger/PassengerDetails'
import { VietQRPayment } from '@/components/payment/VietQRPayment'
import { bookingStep, readBookingSession, reachableStep } from '@/lib/booking-session'
import type { PassengerData, PassengerDraft, BookingStep, BookingSession } from '@/lib/checkout-model'
import type { BookingResponse, FlightResponse, FlightSeatResponse } from '@/types/api'
import { useAuth } from '@/lib/auth-context'
import { CHECKOUT_SESSION_KEY } from '@/lib/private-session'
import { storedBookingId, clearBookingAttempt } from '@/lib/payment'

import { adaptFlight, type AdaptedFlight } from '@/lib/flight'

const getTodayStr = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export default function BookingApp() {
  const { user, isLoading } = useAuth()
  const [identity, setIdentity] = useState<{ id: number | null; revision: number }>({ id: user?.id ?? null, revision: 0 })
  const id = user?.id ?? null
  // Guest checkout may be adopted at login. Leaving a private account discards its mounted UI immediately.
  if (identity.id !== id) setIdentity({ id, revision: identity.revision + (identity.id !== null ? 1 : 0) })
  if (isLoading) return <main className="p-8 text-center" role="status">Loading session...</main>
  return <BookingFlow key={identity.revision} />
}

function BookingFlow() {
  const { user } = useAuth()
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
  const [passengerDraft, setPassengerDraft] = useState<PassengerDraft | null>(null)
  const [holdExpiresAt, setHoldExpiresAt] = useState<string | null>(null)
  const [restored, setRestored] = useState(false)
  const [passengerData, setPassengerData] = useState<PassengerData | null>(null)
  const [recoveryId, setRecoveryId] = useState<number | null>(null)
  const [bookingData, setBookingData] = useState<BookingResponse | null>(null)
  const [selectedFlight, setSelectedFlight] = useState<AdaptedFlight | null>(null)
  const [selectedReturnFlight, setSelectedReturnFlight] = useState<AdaptedFlight | null>(null)

  const handleHome = async () => {
    if (storedBookingId()) clearBookingAttempt()
    setRecoveryId(null)
    setBookingData(null)
    setSelectedFlight(null)
    setSelectedReturnFlight(null)
    setSelectedSeats([])
    setSelectedReturnSeats([])
    setPassengerData(null)
    setPassengerDraft(null)
    setHoldExpiresAt(null)
    navigateTo('home')
  }

  const navigateTo = (newView: BookingStep, pushHistory = true) => {
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

  const currentDraft = { selectedFlight, selectedReturnFlight, passengerData, selectedSeats, selectedReturnSeats, bookingData, holdExpiresAt }
  const currentRef = useRef({ draft: currentDraft, recoveryId })
  useLayoutEffect(() => { currentRef.current = { draft: currentDraft, recoveryId } })
  useEffect(() => {
    const handlePopState = () => {
      const step = bookingStep(new URL(window.location.href).searchParams.get('step'))
      setView(reachableStep(step, currentRef.current.draft, currentRef.current.recoveryId))
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])
  useEffect(() => {
    const confirmedId = storedBookingId()
    setRecoveryId(confirmedId)
    let raw: string | null = null
    try { raw = sessionStorage.getItem(CHECKOUT_SESSION_KEY) } catch { /* Continue with a fresh in-memory draft. */ }
    const saved = readBookingSession(raw, user?.id ?? null)
    if (saved) {
      setSelectedFlight(saved.selectedFlight); setSelectedReturnFlight(saved.selectedReturnFlight)
      setSearchParams(saved.searchParams); setPassengerData(saved.passengerData); setPassengerDraft(saved.passengerDraft)
      setSelectedSeats(saved.selectedSeats); setSelectedReturnSeats(saved.selectedReturnSeats)
      setBookingData(saved.bookingData); setHoldExpiresAt(saved.holdExpiresAt)
      const query = new URL(window.location.href).searchParams.get('step')
      setView(reachableStep(query ? bookingStep(query) : saved.view, saved, confirmedId))
    } else {
      try { sessionStorage.removeItem(CHECKOUT_SESSION_KEY) } catch { /* Storage unavailable. */ }
      if (confirmedId) setView('payment')
    }
    setRestored(true)
  }, [])
  useEffect(() => {
    if (!restored) return
    if (view === 'home') { try { sessionStorage.removeItem(CHECKOUT_SESSION_KEY) } catch { /* Storage unavailable. */ } return }
    const session: BookingSession = { version: 3, ownerId: user?.id ?? null, updatedAt: Date.now(), view, searchParams,
      selectedFlight, selectedReturnFlight, passengerData, passengerDraft, selectedSeats, selectedReturnSeats, bookingData, holdExpiresAt }
    try { sessionStorage.setItem(CHECKOUT_SESSION_KEY, JSON.stringify(session)) } catch { /* Unknown-create marker has its own fail-closed storage path. */ }
  }, [restored, view, searchParams, selectedFlight, selectedReturnFlight, passengerData, passengerDraft, selectedSeats, selectedReturnSeats, bookingData, holdExpiresAt, user?.id])
  const savePassengerDraft = useCallback((draft: PassengerDraft) => setPassengerDraft(draft), [])

  const handleFlightSelect = async (outbound: FlightResponse, returnFlight?: FlightResponse) => {
    setBookingData(null)
    setRecoveryId(null)
    setPassengerData(null)
    setPassengerDraft(null)
    setHoldExpiresAt(null)
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

  if (!restored) return <main className="p-8" role="status">Restoring booking...</main>

  return view === 'home' ? (
    <HomePage 
      onSearch={(params) => {
        setPassengerData(null); setPassengerDraft(null); setSelectedFlight(null); setSelectedReturnFlight(null); setSelectedSeats([]); setSelectedReturnSeats([]); setHoldExpiresAt(null); setBookingData(null); setRecoveryId(null)
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
      passengerData={passengerDraft ? { ...passengerData, ...passengerDraft } : passengerData}
      onDraftChange={savePassengerDraft}
      onBack={() => navigateTo('results')} 
      onHome={handleHome} 
      onContinue={async (data) => { 
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
      cabinClass={searchParams.cabinClass}
      initialSeats={selectedSeats}
      initialReturnSeats={selectedReturnSeats}
      onBack={() => navigateTo('passengers')} 
      onHome={handleHome} 
      onContinue={(seats, booking, returnSeats, expiresAt) => {
        setHoldExpiresAt(expiresAt || null)
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
      holdExpiresAt={holdExpiresAt}
      bookingId={recoveryId || undefined}
      onBookingCreated={(booking) => setBookingData(booking)}
      onBack={() => navigateTo('seats')} 
      onHome={handleHome} 
    />
  )
}
