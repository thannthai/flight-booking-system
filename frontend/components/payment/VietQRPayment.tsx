'use client'

import Image from 'next/image'
import { useRouter } from 'next/navigation'

import { SkywayBrand } from '@/components/SkywayBrand'

import { useCallback, useEffect, useRef, useState, useLayoutEffect } from 'react'
import { Loader2, CheckCircle2, CircleX, Clock3, TriangleAlert } from 'lucide-react'
import QRCode from 'qrcode'
import { Button } from '@/components/ui/button'
import { BoardingPassModal } from '@/components/booking/BoardingPassModal'
import { TripSummary } from './TripSummary'
import { TransferDetails } from './TransferDetails'
import { MyBookingsModal } from '@/components/booking/MyBookingsModal'
import { UserNav } from '@/components/auth/UserNav'
import { bookingStatusLabel, expiredBooking } from '@/lib/booking-status'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import { formatCountdown, formatVND, secondsUntil } from '@/lib/format'
import { checkoutUrl, createBookingOnce, hasUnresolvedBooking, paymentView, preparePayment, readPayment, type PaymentSnapshot } from '@/lib/payment'
import type { BookingResponse, FlightSeatResponse } from '@/types/api'
import type { AdaptedFlight } from '@/lib/flight'
import type { CheckoutData } from '@/lib/checkout-model'
import { priceBooking } from '@/lib/pricing'
import { passengerType } from '@/lib/checkout-model'
import { buildBookingRequest } from '@/lib/checkout-requests'

interface Props {
  flight?: { id: number } & Partial<AdaptedFlight> | null
  returnFlight?: { id: number } & Partial<AdaptedFlight> | null
  seats?: (FlightSeatResponse | null)[]
  returnSeats?: (FlightSeatResponse | null)[]
  passengerData?: CheckoutData | null
  bookingData?: BookingResponse | null
  holdExpiresAt?: string | null
  bookingId?: number
  onBookingCreated?: (booking: BookingResponse) => void
  onBack?: () => void
  onHome: () => void
}

export function VietQRPayment(props: Props) {
  return <PaymentCheckout key={props.bookingId || props.bookingData?.id || 'draft'} {...props} />
}

function PaymentCheckout({ flight, returnFlight, seats = [], returnSeats = [], passengerData, bookingData, holdExpiresAt, bookingId, onBookingCreated, onBack, onHome }: Props) {
  const { user } = useAuth()
  const router = useRouter()
  const id = bookingId || bookingData?.id
  const [snapshot, setSnapshot] = useState<PaymentSnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [encodedQr, setEncodedQr] = useState<{ payload: string; dataUrl: string } | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [showTickets, setShowTickets] = useState(false)
  const [showBookings, setShowBookings] = useState(false)
  const [unresolved, setUnresolved] = useState(hasUnresolvedBooking)
  const mounted = useRef(true)
  const generation = useRef({ value: 0 })
  const working = useRef(false)
  const foregroundLoading = useRef(false)
  const initializationError = useRef<string | null>(null)
  const onCreated = useRef(onBookingCreated)
  useLayoutEffect(() => { onCreated.current = onBookingCreated }, [onBookingCreated])
  useEffect(() => {
    const fence = generation.current
    mounted.current = true
    return () => { mounted.current = false; fence.value++ }
  }, [])

  const load = useCallback(async (prepare = false, background = false) => {
    if (!id) return
    if (background && foregroundLoading.current) return false
    if (!background) foregroundLoading.current = true
    const version = ++generation.current.value
    if (!background) setBusy(true)
    try {
      const next = await (prepare ? preparePayment(id) : readPayment(id))
      if (mounted.current && version === generation.current.value) {
        setSnapshot(next)
        if (!['initializing', 'ready'].includes(paymentView(next))) initializationError.current = null
        setError(initializationError.current)
      }
      return next.booking.status !== 'PENDING' || ['SUCCESS','CANCELLED','EXPIRED','PAID_AFTER_TERMINAL'].includes(next.status.paymentStatus)
    } catch (e) {
      if (mounted.current && version === generation.current.value) {
        // A failed status read hides stale QR until the server can be reached again.
        setSnapshot(null)
        const message = e instanceof Error ? e.message : 'Could not check payment. Your booking has not been cancelled.'
        if (prepare) initializationError.current = message
        setError(message)
      }
    } finally {
      if (!background) {
        foregroundLoading.current = false
        if (mounted.current && version === generation.current.value) setBusy(false)
      }
    }
  }, [id])

  useEffect(() => {
    const fence = generation.current
    let stopped = false
    let timer: ReturnType<typeof setTimeout>
    const poll = async (first: boolean) => {
      const terminal = await load(first, !first)
      if (!stopped && !terminal) timer = setTimeout(() => { void poll(false) }, 3000)
    }
    if (id) void poll(true)
    return () => { stopped = true; clearTimeout(timer); fence.value++ }
  }, [id, load])

  const view = snapshot ? paymentView(snapshot, now) : null
  useEffect(() => {
    if (id && view !== 'pending' && view !== 'ready' && view !== 'initializing') return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [view, id])
  const payment = snapshot?.status.payment
  const demo = payment?.provider === 'DEMO'
  const legacyDemo = demo && !user?.demoAlias
  const url = checkoutUrl(payment?.paymentUrl)
  const payload = (view === 'pending' && !error && !legacyDemo) ? (demo ? `SKYWAY-DEMO-NOT-PAYABLE:${id}:${payment?.id}` : payment?.qrCode) : undefined
  const qr = encodedQr?.payload === payload ? encodedQr?.dataUrl : null
  useEffect(() => {
    let active = true
    if (payload) {
      // payOS supplies the EMV payload. Encode locally, never reconstruct bank details or leak it to an image service.
      QRCode.toDataURL(payload, { width: 280, margin: 2, errorCorrectionLevel: 'M' })
        .then(value => { if (active) setEncodedQr({ payload, dataUrl: value }) })
        .catch(() => { if (active) setError('QR could not be displayed. Check status or use the secure payOS checkout.') })
    }
    return () => { active = false }
  }, [payload])

  const estimate = !id && passengerData && flight?.basePrice != null ? (() => {
    const types = passengerData.paxList.map(p => passengerType(p.type))
    const eligible = types.flatMap((type, index) => type === 'INFANT' ? [] : [index])
    const legs = returnFlight?.basePrice != null ? [flight.basePrice!, returnFlight.basePrice] : [flight.basePrice!]
    const selected = [seats, ...(returnFlight ? [returnSeats] : [])].flatMap((list, leg) => eligible.map((passengerIndex, index) => ({passengerIndex, price:list[index]?.price ?? legs[leg]})))
    return priceBooking(legs, types, selected, passengerData.passengerAddOns)
  })() : null

  const holdExpired = !id && (!holdExpiresAt || Date.parse(holdExpiresAt) <= now)
  const create = async () => {
    if (holdExpired) { setError('Seat hold expired. Return to seats to request availability again.'); return }
    if (working.current || !flight || !passengerData) return
    working.current = true
    setBusy(true)
    setError(null)
    try {
      const request = buildBookingRequest({ flight, returnFlight, seats, returnSeats, passengerData })
      const booking = await createBookingOnce(request)
      if (mounted.current) onCreated.current?.(booking)
    } catch (e) { if (mounted.current) setError(e instanceof Error ? e.message : 'Booking could not be confirmed.') }
    finally { working.current = false; if (mounted.current) { setBusy(false); setUnresolved(hasUnresolvedBooking()) } }
  }

  const cancel = async () => {
    if (!id || working.current || !window.confirm('Cancel this pending booking?')) return
    working.current = true
    setBusy(true)
    try { await api.cancelBooking(id); await load() }
    catch (e) { setError(e instanceof Error ? e.message : 'Cancellation failed. Check status before continuing.') }
    finally { working.current = false; setBusy(false) }
  }
  const confirmDemo = async () => {
    if (!payment || !id || working.current) return
    working.current = true
    setBusy(true)
    try {
      await api.post(`/payments/${payment.id}/demo-confirm`)
      await load()
    } catch (e) {
      if (mounted.current) setError(e instanceof Error ? e.message : 'Confirmation could not be verified. Check payment status before continuing.')
    } finally { working.current = false; if (mounted.current) setBusy(false) }
  }
  const expiryFinalized = !!snapshot && expiredBooking(snapshot.booking)
  const terminal = view === 'cancelled' || view === 'expired' || view === 'refund' || view === 'unavailable'
  const contentWidth = terminal ? 'max-w-4xl' : view === 'pending' && !error ? demo ? 'max-w-lg' : 'max-w-4xl' : 'max-w-6xl'
  const messages = {
    success: 'Payment confirmed. Your e-tickets are available.',
    cancelled: 'Booking or payment cancelled. Do not transfer money using an old QR.',
    expired: expiryFinalized ? 'This booking was automatically closed because payment was not completed in time.' : 'We are checking the final payment result. Do not use the old QR or make a new transfer.',
    refund: 'Payment received after a terminal state or awaiting refund review. No new tickets will be issued. Contact support for reconciliation.',
    unavailable: 'This booking is not available for payment.',
    ready: 'Ready to initialize payment.',
    initializing: 'Payment checkout is not ready. No QR or checkout link is available yet. Retry initialization after checking payment configuration or connection.',
    pending: 'Awaiting payment confirmation from the backend. Returning from payOS alone does not confirm payment.',
  }
  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950 pb-20">
      <header className="bg-white border-b border-slate-200">
        <div className="mx-auto flex min-h-[72px] max-w-7xl items-center justify-between gap-3 px-5 lg:px-8">
          <button disabled={busy && !id} onClick={onHome} className="flex items-center gap-3 font-bold text-[#102a56]" aria-label="Skyway home">
              <SkywayBrand />
            </button>
          <UserNav />
        </div>
      </header>
      <section className="bg-[#102a56] text-white border-b-4 border-[#f59e0b]">
        <div className={`mx-auto px-5 py-6 ${contentWidth}`}>
          <h1 className="text-2xl font-bold">{view === 'success' ? 'Booking confirmed' : view === 'cancelled' ? snapshot?.booking.status === 'CANCELLED' ? 'Booking cancelled' : 'Payment cancelled' : view === 'expired' ? expiryFinalized ? 'Booking expired' : 'Payment deadline reached' : id ? `Payment · ${snapshot?.booking.pnr || `Booking #${id}`}` : 'Review your booking'}</h1>

        </div>
      </section>
      <div className={`mx-auto px-5 ${contentWidth} ${view === 'pending' && !error ? 'py-6 sm:py-8' : 'py-10'}`}>
        {terminal ? <section aria-label="Payment result" className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
          <div className={`mb-5 flex size-12 items-center justify-center rounded-full ${view === 'cancelled' ? 'bg-slate-100 text-slate-600' : 'bg-amber-50 text-amber-700'}`}>
            {view === 'cancelled' ? <CircleX className="size-6" /> : view === 'expired' ? <Clock3 className="size-6" /> : <TriangleAlert className="size-6" />}
          </div>
          <h2 className="text-2xl font-semibold text-[#102a56]">{view === 'cancelled' ? snapshot?.booking.status === 'CANCELLED' ? 'Your booking has been cancelled' : 'This payment has been cancelled' : view === 'expired' ? expiryFinalized ? 'Your booking has expired' : 'The payment window has ended' : view === 'refund' ? 'Payment needs review' : 'Payment is unavailable'}</h2>
          <p className="mt-3 text-sm leading-relaxed text-slate-600" role="status">{messages[view]}</p>
          {view === 'cancelled' && snapshot?.booking.status === 'CANCELLED' && <p className="mt-2 text-sm text-slate-600">Your reservation is closed. You can search for another flight from Home.</p>}
          {snapshot && <dl className="mt-6 grid grid-cols-2 gap-4 rounded-xl bg-slate-50 p-4 text-sm"><div><dt className="text-xs text-slate-500">Booking reference</dt><dd className="mt-1 font-semibold text-[#102a56]">{snapshot.booking.pnr}</dd></div><div><dt className="text-xs text-slate-500">Booking status</dt><dd className="mt-1 font-semibold text-slate-700">{view === 'expired' ? expiryFinalized ? 'Expired' : 'Verifying payment' : bookingStatusLabel(snapshot.booking, now)}</dd></div></dl>}
          {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}
          <div className="mt-6 flex flex-wrap gap-3"><Button onClick={onHome} className="bg-[#102a56] text-white hover:bg-[#1c4079]">Back to Home</Button><Button variant="outline" onClick={() => setShowBookings(true)}>My Bookings</Button></div>
        </section> : <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
          {view === 'pending' && snapshot && !error && <div aria-label="Payment deadline" className={`items-center gap-3 border-b border-slate-200 bg-white px-5 py-3 ${demo ? 'grid grid-cols-[minmax(0,1fr)_auto]' : 'flex flex-wrap justify-between'}`}>
            <div className="min-w-0"><p className="flex items-center gap-2 text-sm font-semibold text-[#102a56]"><span className="size-2 shrink-0 rounded-full bg-amber-500" />Awaiting {demo ? 'demo confirmation' : 'payment'}</p><p className="mt-1 text-xs text-slate-500">Payment deadline: {new Date(snapshot.booking.paymentDeadline).toLocaleString()}</p></div>
            <div className="flex items-center gap-1.5 rounded-md bg-amber-50 px-2.5 py-1.5 text-amber-900"><Clock3 className="size-3.5" /><span className={demo ? 'sr-only' : 'text-[11px]'}>Time remaining</span><span className="text-base font-semibold tabular-nums">{formatCountdown(secondsUntil(snapshot.booking.paymentDeadline))}</span></div>
          </div>}
          <div className={`grid ${id && view !== 'success' ? demo && view === 'pending' && !error ? '' : 'md:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]' : 'md:grid-cols-[1fr_360px]'}`}>
            <div className={`border-slate-200 ${id && view === 'pending' && !error ? demo ? 'bg-white px-5 pb-0 pt-5' : 'bg-slate-50 p-5 md:border-r' : 'bg-slate-50 p-6 md:p-8 md:border-r'}`}>
              {!id || view === 'success' ? <>
                <h2 className="mb-5 flex items-center gap-2 text-lg font-semibold text-[#102a56]">{view === 'success' && <CheckCircle2 className="size-5 text-emerald-600" />}Your trip</h2>
                <TripSummary booking={snapshot?.booking} flight={flight} returnFlight={returnFlight} seats={seats} returnSeats={returnSeats} passengerData={passengerData} />
              </> : <div className={`flex flex-col items-center text-center ${view === 'pending' && !error ? '' : 'min-h-64 justify-center'}`}>
                {view === 'pending' && !error ? <>
                  <h2 className="mb-3 text-base font-semibold text-[#102a56]">{demo ? 'Simulated payment' : 'Scan to pay'}</h2>
                  {qr && <Image unoptimized width={280} height={280} src={qr} alt={demo ? 'Demo QR — not payable' : 'Payment QR supplied by payOS'} className={`h-auto w-full rounded-lg border border-slate-200 bg-white p-2 ${demo ? 'max-w-52' : 'max-w-64'}`} />}

                  <p className="mt-3 max-w-64 text-xs leading-relaxed text-slate-600">{demo ? 'Sample QR only — not payable.' : 'Scan with your banking app and verify the details. Payment status updates automatically.'}</p>
                </> : <p className="max-w-sm text-sm text-slate-600">{error ? 'Payment could not be loaded. See the error and retry.' : view ? 'Payment is not available. See the current status.' : 'Loading payment…'}</p>}
              </div>}
              {busy && <Loader2 className="mt-4 size-5 animate-spin text-slate-400" aria-label="Checking payment" />}
            </div>
            <div className={view === 'pending' && !error ? 'min-w-0 space-y-4 p-5' : 'p-6 space-y-5'}>
              {!(demo && view === 'pending' && !error) && <h2 className="text-lg font-semibold text-[#102a56]">{!id ? 'Confirm your reservation' : view === 'success' ? 'Payment complete' : 'Transfer details'}</h2>}
              {estimate && <div className="space-y-3 text-sm">
                <div className="flex justify-between text-slate-600"><span>Flights and seats</span><span>{formatVND(estimate.fareAmount)}</span></div>
                <div className="flex justify-between text-slate-600"><span>Optional extras</span><span>{formatVND(estimate.addOnsTotal)}</span></div>
                <div className="flex justify-between text-slate-600"><span>Estimated tax</span><span>{formatVND(estimate.taxAmount)}</span></div>
                <div className="flex justify-between border-t border-slate-200 pt-4 text-lg font-semibold text-[#102a56]"><span>Estimated total</span><span>{formatVND(estimate.totalAmount)}</span></div>
              </div>}
              {snapshot && view !== 'pending' && <><p className="text-sm text-slate-500">Total amount</p><p className="text-2xl font-bold text-[#102a56]">{formatVND(payment?.transferAmount ?? snapshot.booking.totalAmount)}</p></>}
              {payment && view === 'pending' && !error && <TransferDetails key={payment.id} payment={payment} />}
              {legacyDemo && view === 'pending' && <p role="alert" className="text-sm text-amber-900">This reservation has an earlier simulated payment. Cancel it and create a new reservation to pay through payOS. Existing payments cannot be converted.</p>}
              {payment?.provider === 'PAYOS' && view === 'pending' && <p className="text-xs text-slate-500">payOS · Real bank transfer</p>}
              {view && view !== 'pending' && <p role="status" className="text-sm text-slate-600">{messages[view]}</p>}
              {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
              {!id && unresolved && <p role="alert" className="text-sm text-amber-800">A previous reservation request may have succeeded. Open My Bookings to recover it. This page will not resend that request.</p>}
              {holdExpired && passengerData && <p role="alert" className="text-sm text-amber-800">Seat hold expired. Return to seats before confirming a booking.</p>}
              {!id && !unresolved && passengerData && <Button disabled={busy || holdExpired} onClick={create} className="h-11 w-full bg-[#102a56] text-white hover:bg-[#1c4079]">Confirm booking &amp; continue to payment</Button>}
              <div className={demo && view === 'pending' && !error ? 'flex flex-col gap-2 sm:flex-row' : 'flex flex-wrap gap-3 border-t border-slate-100 pt-4'}>
              {demo && !legacyDemo && view === 'pending' && !error && <Button className="min-h-11 bg-[#102a56] text-white hover:bg-[#1c4079] sm:flex-1" disabled={busy} onClick={confirmDemo}>Confirm Demo Payment</Button>}
              {!id && !unresolved && onBack && <Button disabled={busy} variant="outline" onClick={onBack}>Back to Seats</Button>}
              {id && view !== 'success' && (error || view === 'ready' || view === 'initializing') && <Button disabled={busy} onClick={() => void load(!snapshot || view === 'ready' || view === 'initializing')} variant="outline">Retry</Button>}
              {!demo && view === 'pending' && url && <a href={url} target="_blank" rel="noopener noreferrer" className="inline-flex min-h-11 items-center text-sm font-semibold text-[#102a56] underline underline-offset-4 hover:text-[#1c4079] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600">Open secure payOS checkout</a>}
              {view === 'success' && snapshot && <Button className="bg-[#102a56] text-white hover:bg-[#1c4079]" onClick={() => setShowTickets(true)}>View Tickets</Button>}
              {(view === 'ready' || view === 'initializing' || view === 'pending') && <Button className="min-h-11 p-0 hover:bg-transparent" disabled={busy} onClick={cancel} variant="ghost"><span className="inline-flex h-8 items-center rounded-md border border-slate-200 bg-white px-2.5 text-xs transition-colors group-hover/button:bg-slate-50">Cancel Booking</span></Button>}
              {view === 'success' && <Button variant="outline" onClick={() => setShowBookings(true)}>My Bookings</Button>}
              </div>
              {view === 'success' && <button type="button" onClick={onHome} className="cursor-pointer text-sm font-medium text-slate-600 underline underline-offset-4">Back to Home</button>}
              {id && (view === 'pending' || view === 'ready' || view === 'initializing') && !(view === 'pending' && !error) && <p className="text-xs leading-relaxed text-slate-500">Leaving this page does not cancel your booking. Seats are held only until the deadline shown above. To return, open My Bookings and choose Continue payment.</p>}
            </div>
          </div>
          {view === 'pending' && !error && <p className="border-t border-slate-200 px-5 py-3 text-xs leading-relaxed text-slate-600">{demo ? 'Your booking stays pending until its deadline. Resume payment from My Bookings.' : 'Leaving this page does not cancel your booking. Seats are held only until the deadline shown above. To return, open My Bookings and choose Continue payment.'}</p>}
        </div>}
        {id && snapshot && view !== 'success' && <details className={`rounded-2xl border border-slate-200 bg-white ${terminal ? 'mt-6 p-6 sm:p-8' : 'mt-5 p-5'}`}><summary className="cursor-pointer font-semibold text-[#102a56]">Trip details</summary><div className="mt-5"><TripSummary booking={snapshot.booking} /></div></details>}
      </div>
      <MyBookingsModal isOpen={showBookings} onClose={() => setShowBookings(false)} onPayBooking={booking => router.push(`/payment/status?bookingId=${booking.id}`)} />
      <BoardingPassModal isOpen={showTickets && view === 'success'} onClose={() => setShowTickets(false)} booking={snapshot?.booking || null} />
    </main>
  )
}
