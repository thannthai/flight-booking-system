export function apiProxyDestination(value, production = false) {
  if (!value && production) throw new Error('BACKEND_API_URL is required in production (HTTPS backend API base ending /api/v1).')
  const url = new URL(value || 'http://localhost:8080/api/v1')
  if (url.username || url.password || url.search || url.hash || !['http:', 'https:'].includes(url.protocol)) throw new Error('Invalid BACKEND_API_URL')
  if (production && url.protocol !== 'https:') throw new Error('Production BACKEND_API_URL must use HTTPS')
  if (url.pathname.replace(/\/$/, '') !== '/api/v1') throw new Error('BACKEND_API_URL must end in /api/v1')
  return url.href.replace(/\/$/, '')
}
export function paymentReturnBookingId(value) {
  if (!value || !/^[1-9]\d*$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
}
