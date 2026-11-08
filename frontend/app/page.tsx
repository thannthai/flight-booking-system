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
import { paymentReturnBookingId } from '@/lib/deployment.mjs'

import { adaptFlight, type AdaptedFlight } from '@/lib/flight'

const getTodayStr = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function initialBookingState(ownerId: number | null) {
  const query = new URLSearchParams(typeof window === 'undefined' ? '' : window.location.search)
  const step = query.get('step')
  const requestedId = step === 'payment' ? paymentReturnBookingId(query.get('bookingId')) : null
  const confirmedId = storedBookingId()
  let raw: string | null = null
  if (!requestedId) {
    try { raw = sessionStorage.getItem(CHECKOUT_SESSION_KEY) } catch { /* Use an in-memory draft. */ }
  }
  const saved = requestedId ? null : readBookingSession(raw, ownerId)
  const recoveryId = requestedId || (step === 'payment' ? confirmedId : null)
  const restoredStep = saved ? step ? bookingStep(step)
    : saved.view === 'payment' && (saved.bookingData?.id || confirmedId) ? 'home' : saved.view : 'home'
  const view: BookingStep = requestedId || (!saved && confirmedId && step === 'payment') ? 'payment'
    : saved ? reachableStep(restoredStep, saved, confirmedId) : 'home'
  return { saved, recoveryId, view, clearHomeAttempt: !step && !!confirmedId, discardDraft: !requestedId && !saved }
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
  const { user, openAuthModal } = useAuth()
  const [initial] = useState(() => initialBookingState(user?.id ?? null))
  const [view, setView] = useState<BookingStep>(initial.view)
  const [searchParams, setSearchParams] = useState<SearchParams>(() => initial.saved?.searchParams ?? ({
    originCode: 'SGN',
    destinationCode: 'HAN',
    departureDate: getTodayStr(),
    returnDate: null,
    passengers: { adults: 1, children: 0, infants: 0 },
    cabinClass: 'ECONOMY'
  }))
  const [selectedSeats, setSelectedSeats] = useState<FlightSeatResponse[]>(initial.saved?.selectedSeats ?? [])
  const [selectedReturnSeats, setSelectedReturnSeats] = useState<FlightSeatResponse[]>(initial.saved?.selectedReturnSeats ?? [])
  const [passengerDraft, setPassengerDraft] = useState<PassengerDraft | null>(initial.saved?.passengerDraft ?? null)
  const [holdExpiresAt, setHoldExpiresAt] = useState<string | null>(initial.saved?.holdExpiresAt ?? null)
  const [passengerData, setPassengerData] = useState<PassengerData | null>(initial.saved?.passengerData ?? null)
  const [recoveryId, setRecoveryId] = useState<number | null>(initial.recoveryId)
  const [bookingData, setBookingData] = useState<BookingResponse | null>(initial.saved?.bookingData ?? null)
  const [selectedFlight, setSelectedFlight] = useState<AdaptedFlight | null>(initial.saved?.selectedFlight ?? null)
  const [selectedReturnFlight, setSelectedReturnFlight] = useState<AdaptedFlight | null>(initial.saved?.selectedReturnFlight ?? null)

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
        url.searchParams.delete('bookingId')
      } else {
        url.searchParams.set('step', newView)
        if (newView !== 'payment') url.searchParams.delete('bookingId')
      }
      window.history.pushState({ step: newView }, '', url.toString())
    }
  }

  const currentDraft = { selectedFlight, selectedReturnFlight, passengerData, selectedSeats, selectedReturnSeats, bookingData, holdExpiresAt }
  const currentRef = useRef({ draft: currentDraft, recoveryId })
  useLayoutEffect(() => { currentRef.current = { draft: currentDraft, recoveryId } })
  useEffect(() => {
    const handlePopState = () => {
      const query = new URL(window.location.href).searchParams
      const step = bookingStep(query.get('step'))
      const bookingId = step === 'payment' ? paymentReturnBookingId(query.get('bookingId')) : null
      setRecoveryId(bookingId)
      setView(reachableStep(step, currentRef.current.draft, bookingId || currentRef.current.recoveryId))
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])
  useEffect(() => {
    // Entering Home intentionally leaves a known reservation in My Bookings.
    // An unknown request outcome must retain its retry fence.
    if (initial.clearHomeAttempt) clearBookingAttempt()
    if (initial.discardDraft) {
      try { sessionStorage.removeItem(CHECKOUT_SESSION_KEY) } catch { /* Storage unavailable. */ }
    }
  }, [initial])
  useEffect(() => {
    if (view === 'home' || (recoveryId && !user?.id)) { try { sessionStorage.removeItem(CHECKOUT_SESSION_KEY) } catch { /* Storage unavailable. */ } return }
    const session: BookingSession = { version: 3, ownerId: user?.id ?? null, updatedAt: Date.now(), view, searchParams,
      selectedFlight, selectedReturnFlight, passengerData, passengerDraft, selectedSeats, selectedReturnSeats, bookingData, holdExpiresAt }
    try { sessionStorage.setItem(CHECKOUT_SESSION_KEY, JSON.stringify(session)) } catch { /* Unknown-create marker has its own fail-closed storage path. */ }
  }, [view, searchParams, selectedFlight, selectedReturnFlight, passengerData, passengerDraft, selectedSeats, selectedReturnSeats, bookingData, holdExpiresAt, user?.id, recoveryId])
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

  if (view === 'payment' && recoveryId && !user) return <main className="space-y-4 p-8">
    <p>Sign in to continue payment for this booking.</p>
    <button type="button" onClick={() => openAuthModal('login')} className="cursor-pointer rounded-lg bg-[#102a56] px-5 py-2 text-white">Sign In</button>
    <button type="button" onClick={handleHome} className="ml-4 cursor-pointer text-sm underline">Home</button>
  </main>

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
      onBookingCreated={(booking) => {
        setBookingData(booking)
        const url = new URL(window.location.href)
        url.searchParams.set('step', 'payment')
        url.searchParams.set('bookingId', String(booking.id))
        window.history.replaceState({ step: 'payment' }, '', url.toString())
      }}
      onBack={() => navigateTo('seats')} 
      onHome={handleHome} 
    />
  )
}
