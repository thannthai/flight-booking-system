import type { BookingResponse } from '@/types/api'

export function expiredBooking(booking: BookingResponse): boolean {
  return booking.status === 'CANCELLED' && booking.cancellationReason === 'PAYMENT_TIMEOUT'
}

export function bookingStatusLabel(booking: BookingResponse, now = Date.now()): string {
  if (expiredBooking(booking)) return 'Expired'
  if (booking.status === 'CANCELLED') return 'Cancelled'
  if (booking.status === 'PENDING') return Date.parse(booking.paymentDeadline) <= now ? 'Verifying payment' : 'Awaiting payment'
  return booking.status
}
