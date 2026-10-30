'use client'

import { useCallback, useEffect, useRef, useState, useLayoutEffect } from 'react'
import { Plane, Loader2 } from 'lucide-react'
import QRCode from 'qrcode'
import { Button } from '@/components/ui/button'
import { BoardingPassModal } from '@/components/booking/BoardingPassModal'
import { UserNav } from '@/components/auth/UserNav'
import { api } from '@/lib/api'
import { formatCountdown, formatVND, secondsUntil } from '@/lib/format'
import { checkoutUrl, createBookingOnce, hasUnresolvedBooking, paymentView, preparePayment, readPayment, type PaymentSnapshot } from '@/lib/payment'
import type { BookingResponse, FlightSeatResponse } from '@/types/api'
import type { CheckoutData } from '@/lib/checkout-model'
import { buildBookingRequest } from '@/lib/checkout-requests'

interface Props {
  flight?: { id: number } | null
  returnFlight?: { id: number } | null
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

export function VietQRPayment({ flight, returnFlight, seats = [], returnSeats = [], passengerData, bookingData, holdExpiresAt, bookingId, onBookingCreated, onBack, onHome }: Props) {
  const id = bookingId || bookingData?.id
  const [snapshot, setSnapshot] = useState<PaymentSnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [qr, setQr] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [showTickets, setShowTickets] = useState(false)
  const [unresolved, setUnresolved] = useState(false)
  const mounted = useRef(true)
  const generation = useRef(0)
  const working = useRef(false)
  const onCreated = useRef(onBookingCreated)
  useLayoutEffect(() => { onCreated.current = onBookingCreated }, [onBookingCreated])
  useEffect(() => { mounted.current = true; setUnresolved(hasUnresolvedBooking()); return () => { mounted.current = false; generation.current++ } }, [])

  const load = useCallback(async (prepare = false) => {
    if (!id) return
    const version = ++generation.current
    setBusy(true)
    try {
      const next = await (prepare ? preparePayment(id) : readPayment(id))
      if (mounted.current && version === generation.current) { setSnapshot(next); setError(null) }
    } catch (e) {
      if (mounted.current && version === generation.current) {
        // A failed status read hides stale QR until the server can be reached again.
        setSnapshot(null)
        setError(e instanceof Error ? e.message : 'Could not check payment. Your booking has not been cancelled.')
      }
    } finally { if (mounted.current && version === generation.current) setBusy(false) }
  }, [id])

  useEffect(() => {
    setSnapshot(null)
    let stopped = false
    let timer: ReturnType<typeof setTimeout>
    const poll = async (first: boolean) => {
      await load(first)
      if (!stopped) timer = setTimeout(() => { void poll(false) }, 3000)
    }
    if (id) void poll(true)
    return () => { stopped = true; clearTimeout(timer); generation.current++ }
  }, [id, load])
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const view = snapshot ? paymentView(snapshot, now) : null
  const payment = snapshot?.status.payment
  const url = checkoutUrl(payment?.paymentUrl)
  const payload = (view === 'pending' && !error) ? payment?.qrCode : undefined
  useEffect(() => {
    let active = true
    setQr(null)
    if (payload) {
      // payOS supplies the EMV payload. Encode locally, never reconstruct bank details or leak it to an image service.
      QRCode.toDataURL(payload, { width: 280, margin: 2, errorCorrectionLevel: 'M' })
        .then(value => { if (active) setQr(value) })
        .catch(() => { if (active) setError('QR could not be displayed. Check status or use the secure payOS checkout.') })
    }
    return () => { active = false }
  }, [payload])

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
  const messages = {
    success: 'Payment confirmed. Your e-tickets are available.',
    cancelled: 'Booking or payment cancelled. Do not transfer money using an old QR.',
    expired: 'The payment deadline has been reached. Check server status; do not use an old QR.',
    refund: 'Payment received after a terminal state or awaiting refund review. No new tickets will be issued. Contact support for reconciliation.',
    unavailable: 'This booking is not available for payment.',
    ready: 'Ready to initialize payOS checkout.',
    pending: 'Awaiting payment confirmation from the backend. Returning from payOS alone does not confirm payment.',
  }
  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950 pb-20">
      <header className="bg-white border-b border-slate-200">
        <div className="mx-auto flex min-h-[72px] max-w-7xl items-center justify-between gap-3 px-5 lg:px-8">
          <button disabled={busy && !id} onClick={onHome} className="flex items-center gap-3 font-bold text-[#102a56]" aria-label="Skyway home"><Plane className="size-6" />skyway.</button>
          <UserNav />
        </div>
      </header>
      <section className="bg-[#102a56] text-white border-b-4 border-[#f59e0b]">
        <div className="mx-auto max-w-4xl px-5 py-6">
          <h1 className="text-2xl font-bold">{id ? `Payment · ${snapshot?.booking.pnr || `Booking #${id}`}` : 'Review & Checkout'}</h1>
          {snapshot && <p className="mt-2 text-sm">Payment deadline: {new Date(snapshot.booking.paymentDeadline).toLocaleString()} · Estimated time remaining: {formatCountdown(secondsUntil(snapshot.booking.paymentDeadline))}</p>}
        </div>
      </section>
      <div className="mx-auto max-w-4xl px-5 py-10">
        <div className="bg-white rounded-3xl shadow-sm border border-slate-200 overflow-hidden">
          <div className="grid md:grid-cols-2">
            <div className="p-8 border-b md:border-b-0 md:border-r border-slate-100 flex flex-col items-center justify-center min-h-80">
              {qr && view === 'pending' && !error ? <img src={qr} alt="Payment QR supplied by payOS" className="w-64 h-64" /> : <Plane className="size-16 text-slate-300" />}
              {busy && <Loader2 className="size-6 animate-spin mt-4" aria-label="Checking payment" />}
              {!id && <p className="text-sm text-slate-500 mt-6 text-center">Confirm your reservation to obtain the final server total and payOS checkout.</p>}
            </div>
            <div className="p-8 space-y-5">
              {snapshot && <><p className="text-sm text-slate-500">Total confirmed by backend</p><p className="text-2xl font-bold text-[#102a56]">{formatVND(snapshot.booking.totalAmount)}</p></>}
              {view && <p role="status" className="text-sm text-slate-600">{messages[view]}</p>}
              {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
              {!id && unresolved && <p role="alert" className="text-sm text-amber-800">A previous reservation request may have succeeded. Open My Bookings to recover it. This page will not resend that request.</p>}
              {holdExpired && passengerData && <p role="alert" className="text-sm text-amber-800">Seat hold expired. Return to seats before confirming a booking.</p>}
              {!id && !unresolved && passengerData && <Button disabled={busy || holdExpired} onClick={create} className="w-full bg-[#f59e0b] text-[#102a56]">Confirm Booking</Button>}
              {!id && !unresolved && onBack && <Button disabled={busy} variant="outline" onClick={onBack}>Back to Seats</Button>}
              {id && <Button disabled={busy} onClick={() => void load(true)} variant="outline">{view === 'ready' ? 'Initialize Payment' : 'Check / Retry Payment Status'}</Button>}
              {view === 'pending' && url && <a href={url} target="_blank" rel="noopener noreferrer" className="block text-blue-700 font-semibold underline">Open secure payOS checkout</a>}
              {view === 'success' && snapshot && <Button onClick={() => setShowTickets(true)}>View E-Tickets</Button>}
              {(view === 'ready' || view === 'pending') && <Button disabled={busy} onClick={cancel} variant="outline">Cancel Booking</Button>}
              <Button disabled={busy && !id} onClick={onHome} variant="outline">Back to Home</Button>
            </div>
          </div>
        </div>
      </div>
      <BoardingPassModal isOpen={showTickets && view === 'success'} onClose={() => setShowTickets(false)} booking={snapshot?.booking || null} />
    </main>
  )
}
