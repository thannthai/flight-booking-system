import type { ApiResponse, AuthResponse, UserResponse, LoginRequest, RegisterRequest } from '@/types/api'
import { ApiError, getBaseUrl, fetchWithTimeout, requestJson } from '@/lib/http'

// Serialize shared-cookie mutations across same-origin tabs. Tokens stay in memory.
// Older/non-secure browsers retain per-tab single-flight; backend still rejects replay.
function withAuthCookieLock<T>(operation: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request('skyway-auth-cookie', operation)
  }
  return operation()
}

export interface AuthState { token: string | null; user: UserResponse | null }
export function isTokenExpired(token: string | null): boolean {
  if (!token) return true
  try {
    const part = token.split('.')[1]
    const payload = JSON.parse(atob(part.replace(/-/g, '+').replace(/_/g, '/')))
    return !Number.isFinite(payload.exp) || Date.now() >= payload.exp * 1000 - 30000
  } catch { return true }
}
// Each client owns one lifecycle. The application uses one client; tests can construct isolated clients.
export function createAuthSession(clearPrivateSession: () => void) {
  const emptyAuth: AuthState = { token: null, user: null }
  let auth: AuthState = emptyAuth
  let epoch = 0
  let signedOut = false
  let refreshPromise: Promise<string | null> | null = null
  const listeners = new Set<() => void>()
  const getAuthState = () => auth
  const getServerAuthState = () => emptyAuth
  function subscribeAuth(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } }
  function publish(token: string | null, user: UserResponse | null) {
    if (auth.user && auth.user.id !== user?.id) { epoch++; clearPrivateSession() }
    auth = { token, user }
    listeners.forEach(listener => listener())
  }
  function setAccessToken(token: string | null): void { publish(token, token ? auth.user : null) }
  function getAccessToken(): string | null { return isTokenExpired(auth.token) ? null : auth.token }
  function getCurrentUser(): UserResponse | null { return auth.user }
  function invalidateAuth(blockRefresh = true) {
    epoch++
    clearPrivateSession()
    signedOut = blockRefresh
    if (typeof window !== 'undefined') {
      try {
        if (blockRefresh) sessionStorage.setItem('skyway_signed_out', '1')
        else sessionStorage.removeItem('skyway_signed_out')
      } catch { /* In-memory block remains authoritative in this tab. */ }
    }
    publish(null, null)
  }
  function isSignedOut() {
    if (signedOut) return true
    try { return typeof window !== 'undefined' && sessionStorage.getItem('skyway_signed_out') === '1' } catch { return false }
  }
  function refreshAccessToken(): Promise<string | null> {
    if (isSignedOut()) return Promise.resolve(null)
    if (refreshPromise) return refreshPromise
    const version = epoch
    const operation = withAuthCookieLock(async () => {
      if (version !== epoch || isSignedOut()) return null
      try {
        const response = await fetchWithTimeout(`${getBaseUrl()}/auth/refresh`, { method: 'POST', credentials: 'include', cache: 'no-store' })
        if (response.ok) {
          const json = await response.json() as ApiResponse<AuthResponse>
          if (version !== epoch) return null
          if (json.data?.accessToken && json.data?.user) {
            publish(json.data.accessToken, json.data.user)
            return json.data.accessToken
          }
        }
      } catch { /* Network failure does not erase a still-valid session. */ }
      if (version === epoch && !getAccessToken()) publish(null, null)
      return null
    })
    refreshPromise = operation
    void operation.finally(() => { if (refreshPromise === operation) refreshPromise = null }).catch(() => {})
    return operation
  }

  async function request<T>(method: string, path: string, body?: unknown, isRetry = false): Promise<T> {
    const version = epoch
    const authPath = path.startsWith('/auth/') || /^\/demo\/login\/[AB]$/.test(path)
    const assertCurrent = () => {
      if (!authPath && version !== epoch) throw new ApiError('Account changed.', 'SESSION_CHANGED', 401)
    }
    if (!getAccessToken() && (path.startsWith('/bookings') || path.startsWith('/payments') || path.startsWith('/passengers'))) await refreshAccessToken()
    assertCurrent()
    try {
      return await requestJson<T>(method, path, body, authPath ? null : getAccessToken(), assertCurrent)
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401 || authPath || error.errorCode === 'PARSE_ERROR' || error.errorCode === 'SESSION_CHANGED') throw error
      if (!isRetry) {
        const refreshedToken = await refreshAccessToken()
        assertCurrent()
        if (refreshedToken) return request<T>(method, path, body, true)
      }
      invalidateAuth(false)
      if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('skyway:unauthorized'))
      throw new ApiError('Session expired or authentication required. Please sign in.', 'UNAUTHORIZED', 401)
    }
  }

  async function logout(): Promise<void> {
    const accessToken = auth.token
    invalidateAuth(true)
    // Drain any rotation before revoking the cookie that is now in the browser.
    await refreshPromise
    try {
      const response = await withAuthCookieLock(() => fetchWithTimeout(`${getBaseUrl()}/auth/logout`, {
        method: 'POST', credentials: 'include',
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      }))
      if (!response.ok) throw new Error('Signed out locally; server sign-out could not be confirmed.')
    } finally { broadcastAuth('signout') }
  }

  function broadcastAuth(action: 'signin' | 'signout') {
    if (typeof window !== 'undefined') { try { localStorage.setItem('skyway_auth_changed', JSON.stringify({ action, time: Date.now(), nonce: Math.random() })) } catch { /* Cross-tab broadcast is unavailable without storage. */ } }
  }
  async function authenticate(path: string, payload: LoginRequest | RegisterRequest | undefined): Promise<AuthResponse> {
    await refreshPromise
    const version = ++epoch
    const result = await withAuthCookieLock(async () => {
      if (version !== epoch) throw new ApiError('Authentication changed. Please try again.', 'SESSION_CHANGED', 401)
      return request<AuthResponse>('POST', path, payload)
    })
    if (version !== epoch) throw new ApiError('Authentication changed. Please try again.', 'SESSION_CHANGED', 401)
    signedOut = false
    if (typeof window !== 'undefined') { try { sessionStorage.removeItem('skyway_signed_out') } catch { /* Memory state is sufficient. */ } }
    publish(result.accessToken, result.user)
    broadcastAuth('signin')
    return result
  }

  return {
    request, getAuthState, getServerAuthState, subscribeAuth, setAccessToken, getAccessToken, getCurrentUser, invalidateAuth, refreshAccessToken,
    loginDemo: (alias: 'A' | 'B') => authenticate(`/demo/login/${alias}`, undefined),
    login: (payload: LoginRequest) => authenticate('/auth/login', payload),
    register: (payload: RegisterRequest) => authenticate('/auth/register', payload), logout,
  }
}
