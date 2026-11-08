/** Browser API facade. HTTP, auth coordination and domain endpoints have separate owners. */
import { createAuthSession } from '@/lib/auth-session'
import { createDomainApi } from '@/lib/domain-api'
import { clearPrivateSession } from '@/lib/private-session'

export { ApiError, fetchWithTimeout } from '@/lib/http'
export { isTokenExpired, type AuthState } from '@/lib/auth-session'
export { clearPrivateSession } from '@/lib/private-session'

export function createApiClient() {
  const session = createAuthSession(clearPrivateSession)
  const api = {
    get: <T>(path: string) => session.request<T>('GET', path),
    post: <T>(path: string, body?: unknown) => session.request<T>('POST', path, body),
    put: <T>(path: string, body?: unknown) => session.request<T>('PUT', path, body),
    delete: <T>(path: string) => session.request<T>('DELETE', path),
    loginDemo: session.loginDemo, login: session.login, register: session.register, logout: session.logout,
    ...createDomainApi(session.request),
  }
  return { ...session, api }
}

const client = createApiClient()
export const { api, getAuthState, getServerAuthState, subscribeAuth, setAccessToken, getAccessToken, getCurrentUser, invalidateAuth, refreshAccessToken } = client
