// The visitor's address as Vercel reports it. Only headers Vercel itself sets are read: a client-supplied
// X-Forwarded-For entry could be anything, and the backend uses this value to count requests per visitor.
const ADDRESS = /^[0-9a-fA-F:.]{2,45}$/
const TRUSTED_HEADERS = ['x-vercel-forwarded-for', 'x-real-ip']

export function clientAddress(headers) {
  for (const name of TRUSTED_HEADERS) {
    const value = headers.get(name)?.split(',')[0]?.trim()
    if (value && ADDRESS.test(value)) return value
  }
  return null
}

// Headers for the request forwarded to the backend. Whatever the browser sent as X-Client-IP or
// X-Proxy-Secret is dropped first, so only this server can set them.
export function backendHeaders(incoming, secret) {
  const headers = new Headers(incoming)
  headers.delete('x-client-ip')
  headers.delete('x-proxy-secret')
  const address = clientAddress(incoming)
  if (secret && secret.length >= 16 && address) {
    headers.set('x-client-ip', address)
    headers.set('x-proxy-secret', secret)
  }
  return headers
}
