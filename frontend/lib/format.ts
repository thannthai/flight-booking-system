/**
 * lib/format.ts — Display formatting utilities
 *
 * Centralises all user-visible number, date, and time formatting so that:
 * - VND currency is always displayed consistently (e.g. 1.450.000 ₫)
 * - Prices and countdowns use tabular-nums to prevent layout shifts
 * - Dates/times respect the Vietnamese locale and timezone
 *
 * Every function is pure and has no side effects.
 */

// ---------------------------------------------------------------------------
// Currency — Vietnamese Dong
// ---------------------------------------------------------------------------

/**
 * Format a numeric amount as Vietnamese Dong.
 *
 * formatVND(1450000)    → "1.450.000 ₫"
 * formatVND(990000)     → "990.000 ₫"
 * formatVND(0)          → "0 ₫"
 *
 * Uses vi-VN locale dot-separated thousands grouping, no decimal places.
 * The ₫ symbol is appended with a non-breaking space for readability.
 */
export function formatVND(amount: number): string {
  return (
    new Intl.NumberFormat('vi-VN', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount) + '\u00A0₫'
  )
}

/**
 * Format the total price for a booking with the Vietnamese tax/fee caption.
 * Returns a tuple so callers can style the parts separately.
 *
 * Example:
 *   const [price, caption] = formatBookingTotal(1450000)
 *   // price   → "1.450.000 ₫"
 *   // caption → "(Đã bao gồm thuế, phí & dịch vụ)"
 */
export function formatBookingTotal(amount: number): [string, string] {
  return [formatVND(amount), '(Đã bao gồm thuế, phí & dịch vụ)']
}

// ---------------------------------------------------------------------------
// Countdown / duration
// ---------------------------------------------------------------------------

/**
 * Format a remaining-seconds value as MM:SS.
 *
 * formatCountdown(899) → "14:59"
 * formatCountdown(60)  → "01:00"
 * formatCountdown(0)   → "00:00"
 */
export function formatCountdown(totalSeconds: number): string {
  const clamped = Math.max(0, Math.floor(totalSeconds))
  const mm = Math.floor(clamped / 60).toString().padStart(2, '0')
  const ss = (clamped % 60).toString().padStart(2, '0')
  return `${mm}:${ss}`
}

/**
 * Compute remaining seconds from an ISO-8601 expiry string (OffsetDateTime).
 * Returns 0 if the deadline has already passed.
 *
 * Use this with HoldSeatsResponse.expiresAt or PaymentResponse.expiredAt.
 */
export function secondsUntil(isoExpiry: string): number {
  const expiryMs = new Date(isoExpiry).getTime()
  const nowMs = Date.now()
  return Number.isFinite(expiryMs) ? Math.max(0, Math.floor((expiryMs - nowMs) / 1000)) : 0
}

// ---------------------------------------------------------------------------
// Date and time
// ---------------------------------------------------------------------------

/**
 * Format an ISO-8601 datetime string as a short time (HH:mm) in the
 * Vietnamese timezone (Asia/Ho_Chi_Minh, UTC+7).
 *
 * formatTime("2026-11-14T08:00:00+07:00") → "08:00"
 */
export function formatTime(isoDatetime: string, timeZone = 'Asia/Ho_Chi_Minh'): string {
  if (!Number.isFinite(Date.parse(isoDatetime))) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone,
  }).format(new Date(isoDatetime))
}

/**
 * Format an ISO-8601 datetime string as a readable date.
 *
 * formatDate("2026-11-14T08:00:00+07:00") → "14 thg 11 2026"
 *
 * Pass `locale: 'en-GB'` for the English-style format ("14 Nov 2026").
 */
export function formatDate(
  isoDatetime: string,
  locale: 'vi-VN' | 'en-GB' = 'vi-VN',
  timeZone = 'Asia/Ho_Chi_Minh',
): string {
  if (!Number.isFinite(Date.parse(isoDatetime))) return '—'
  return new Intl.DateTimeFormat(locale, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone,
  }).format(new Date(isoDatetime))
}

/**
 * Format a LocalDate string ("YYYY-MM-DD") as a readable date.
 * Note: LocalDate has no time zone — treated as local midnight.
 *
 * formatLocalDate("1990-01-15") → "15/01/1990"
 */
export function formatLocalDate(localDate: string): string {
  // Parse as UTC midnight to avoid off-by-one from timezone conversion
  const [year, month, day] = localDate.split('-').map(Number)
  return `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}/${year}`
}

/**
 * Compute flight duration string from two ISO-8601 datetime strings.
 * Returns a human-readable string like "2h 15m".
 *
 * This is a frontend-derived value — use only for display.
 * Do not use this value for business logic.
 *
 * NOTE: Once the backend adds a `duration` field to FlightResponse,
 * prefer that over this computed value.
 */
export function computeDuration(
  departureIso: string,
  arrivalIso: string,
): string {
  const diffMs =
    new Date(arrivalIso).getTime() - new Date(departureIso).getTime()
  if (diffMs <= 0) return '—'
  const totalMinutes = Math.floor(diffMs / 60_000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours === 0) return `${minutes}m`
  if (minutes === 0) return `${hours}h`
  return `${hours}h ${minutes}m`
}

/** Calendar date at an airport, independent of browser timezone. */
export function airportDate(iso: string, timeZone = 'Asia/Ho_Chi_Minh'): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(iso))
  const part = (name: string) => parts.find(p => p.type === name)?.value
  return `${part('year')}-${part('month')}-${part('day')}`
}
export function airportHour(iso: string, timeZone = 'Asia/Ho_Chi_Minh'): number {
  return Number(new Intl.DateTimeFormat('en-GB', { timeZone, hour: '2-digit', hourCycle: 'h23' }).format(new Date(iso)))
}
