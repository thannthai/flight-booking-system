export const CHECKOUT_SESSION_KEY = 'skyway_session'
export const BOOKING_ATTEMPT_KEY = 'skyway_booking_attempt'

export function clearPrivateSession() {
  if (typeof window === 'undefined') return
  try { for (const key of [CHECKOUT_SESSION_KEY, BOOKING_ATTEMPT_KEY, 'access_token']) sessionStorage.removeItem(key) } catch { /* Memory auth must still be cleared when storage is unavailable. */ }
  try { localStorage.removeItem('skyway_user') } catch { /* Legacy cache cleanup only. */ }
}
