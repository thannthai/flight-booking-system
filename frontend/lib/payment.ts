import { BOOKING_ATTEMPT_KEY } from '@/lib/private-session'
import { api, ApiError, getCurrentUser } from '@/lib/api'
import type { BookingResponse, CreateBookingRequest, PaymentResponse, PaymentStatusResponse } from '@/types/api'

export interface PaymentClient {
  getBookingById(id: number): Promise<BookingResponse>
  get<T>(path: string): Promise<T>
  post<T>(path: string, body?: unknown): Promise<T>
}
export type PaymentView = 'ready' | 'pending' | 'success' | 'cancelled' | 'expired' | 'refund' | 'unavailable'
export interface PaymentSnapshot {
  booking: BookingResponse
  status: PaymentStatusResponse
}

export function paymentView({ booking, status }: PaymentSnapshot, now = Date.now()): PaymentView {
  if (status.paymentStatus === 'PAID_AFTER_TERMINAL' || (status.payment?.refundStatus && status.payment.refundStatus !== 'NONE')) return 'refund'
  if (booking.status === 'COMPLETED' && status.paymentStatus === 'SUCCESS') return 'success'
  if (booking.status === 'CANCELLED' || status.paymentStatus === 'CANCELLED') return 'cancelled'
  if (status.paymentStatus === 'EXPIRED' || Date.parse(booking.paymentDeadline) <= now) return 'expired'
  if (booking.status !== 'PENDING') return 'unavailable'
  return status.paymentStatus === 'NOT_CREATED' ? 'ready' : status.paymentStatus === 'PENDING' ? 'pending' : 'unavailable'
}

export async function readPayment(bookingId: number, client: PaymentClient = api): Promise<PaymentSnapshot> {
  // Read booking second: a webhook may commit between these reads. Never infer paid from a redirect.
  const status = await client.get<PaymentStatusResponse>(`/payments/booking/${bookingId}`)
  const booking = await client.getBookingById(bookingId)
  return { booking, status }
}

const links = new Map<number, Promise<PaymentSnapshot>>()
export function preparePayment(bookingId: number, client: PaymentClient = api): Promise<PaymentSnapshot> {
  const existing = links.get(bookingId)
  if (existing) return existing
  const operation = (async () => {
    const snapshot = await readPayment(bookingId, client)
    const view = paymentView(snapshot)
    if (view === 'ready' || (view === 'pending' && !snapshot.status.payment?.paymentUrl)) {
      await client.post<PaymentResponse>('/payments/create-link', { bookingId })
      return readPayment(bookingId, client)
    }
    return snapshot
  })()
  links.set(bookingId, operation)
  void operation.finally(() => { if (links.get(bookingId) === operation) links.delete(bookingId) }).catch(() => {})
  return operation
}

export function checkoutUrl(value?: string): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !url.username && !url.password && (url.hostname === 'pay.payos.vn' || url.hostname.endsWith('.payos.vn')) ? url.href : null
  } catch { return null }
}

const ATTEMPT_KEY = BOOKING_ATTEMPT_KEY
export function hasUnresolvedBooking(): boolean {
  try {
    const raw = sessionStorage.getItem(ATTEMPT_KEY)
    if (!raw) return false
    const attempt = JSON.parse(raw)
    if (attempt.ownerId !== (getCurrentUser()?.id ?? null)) { clearBookingAttempt(); return false }
    return true
  } catch { return true }
}
export function clearBookingAttempt(): void { sessionStorage.removeItem(ATTEMPT_KEY) }
export function storedBookingId(): number | null {
  try {
    if (!hasUnresolvedBooking()) return null
    const attempt = JSON.parse(sessionStorage.getItem(ATTEMPT_KEY) || 'null')
    return Number.isSafeInteger(attempt?.bookingId) && attempt.bookingId > 0 ? attempt.bookingId : null
  } catch { return null }
}

export async function createBookingOnce(request: CreateBookingRequest, client: PaymentClient = api): Promise<BookingResponse> {
  // A tab-wide marker survives reload/remount and deliberately blocks unknown-outcome retries.
  if (hasUnresolvedBooking()) throw new Error('Check My Bookings before starting another reservation. The previous request may have succeeded.')
  const actor = getCurrentUser()?.id ?? null
  const marker = JSON.stringify({ startedAt: Date.now(), ownerId: actor })
  sessionStorage.setItem(ATTEMPT_KEY, marker)
  try {
    const booking = await client.post<BookingResponse>('/bookings', request)
    if (actor === (getCurrentUser()?.id ?? null) && sessionStorage.getItem(ATTEMPT_KEY) === marker) sessionStorage.setItem(ATTEMPT_KEY, JSON.stringify({ bookingId: booking.id, ownerId: actor }))
    return booking
  } catch (error) {
    // Only a definitive rejection permits an explicit retry. Network/5xx/parse errors stay fenced.
    if (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.errorCode !== 'PARSE_ERROR' && error.errorCode !== 'SESSION_CHANGED' && error.status !== 408 && sessionStorage.getItem(ATTEMPT_KEY) === marker) clearBookingAttempt()
    throw error
  }
}
