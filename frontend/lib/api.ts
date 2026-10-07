/**
 * lib/api.ts — Base HTTP client
 *
 * Thin fetch wrapper around the Spring Boot backend.
 * - Base URL is read from the NEXT_PUBLIC_API_URL environment variable.
 * - Auth header is injected automatically when an access token is present.
 * - All responses are unwrapped from ApiResponse<T> before returning.
 * - API errors are surfaced as ApiError instances with the backend errorCode.
 *
 * Usage:
 *   import { api } from '@/lib/api'
 *   const airports = await api.get<AirportResponse[]>('/airports')
 *   const booking  = await api.post<BookingResponse>('/bookings', payload)
 */

import type { 
  ApiResponse, 
  ApiErrorResponse, 
  LoginRequest, 
  RegisterRequest, 
  AuthResponse, 
  UserResponse,
  PassengerRequest,
  PassengerResponse,
  BookingResponse,
  PageResponse
} from '@/types/api'

// ---------------------------------------------------------------------------
// Error class
// ---------------------------------------------------------------------------

export class ApiError extends Error {
  readonly errorCode: string
  readonly status: number

  constructor(message: string, errorCode: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.errorCode = errorCode
    this.status = status
  }
}

// ---------------------------------------------------------------------------
// Token management
// ---------------------------------------------------------------------------

let _accessToken: string | null = null

export function isTokenExpired(token: string | null): boolean {
  if (!token) return true
  try {
    const parts = token.split('.')
    if (parts.length < 2) return true
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const decoded = atob(base64)
    const payload = JSON.parse(decoded)
    if (!payload.exp) return false
    // Buffer of 30 seconds to prevent race conditions
    return Date.now() >= (payload.exp * 1000 - 30000)
  } catch {
    return true
  }
}

export function setAccessToken(token: string | null): void {
  _accessToken = token
  if (typeof window !== 'undefined') {
    if (token) {
      sessionStorage.setItem('access_token', token)
    } else {
      sessionStorage.removeItem('access_token')
    }
  }
}

export function getAccessToken(): string | null {
  if (!_accessToken && typeof window !== 'undefined') {
    _accessToken = sessionStorage.getItem('access_token')
  }
  if (_accessToken && isTokenExpired(_accessToken)) {
    setAccessToken(null)
    return null
  }
  return _accessToken
}

export function getCurrentUser(): UserResponse | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = localStorage.getItem('skyway_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export async function refreshAccessToken(): Promise<string | null> {
  try {
    const res = await fetch(`${getBaseUrl()}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    })
    if (res.ok) {
      const json = await res.json()
      if (json.data?.accessToken) {
        setAccessToken(json.data.accessToken)
        if (json.data?.user && typeof window !== 'undefined') {
          localStorage.setItem('skyway_user', JSON.stringify(json.data.user))
        }
        return json.data.accessToken
      }
    }
  } catch (e) {
    console.warn('Token refresh failed:', e)
  }
  setAccessToken(null)
  return null
}

// ---------------------------------------------------------------------------
// Base URL
// ---------------------------------------------------------------------------

function getBaseUrl(): string {
  const url = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'
  return url.endsWith('/') ? url.slice(0, -1) : url
}

// ---------------------------------------------------------------------------
// Core fetch helper
// ---------------------------------------------------------------------------

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  isRetry = false
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }

  // If token is missing or expired, attempt silent refresh for protected routes
  if (!getAccessToken() && (path.startsWith('/bookings') || path.startsWith('/payments') || path.startsWith('/passengers'))) {
    await refreshAccessToken()
  }

  const token = getAccessToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const init: RequestInit = {
    method,
    headers,
    credentials: 'include',
    cache: 'no-store',
  }

  if (body !== undefined) {
    init.body = JSON.stringify(body)
  }

  const response = await fetch(`${getBaseUrl()}${path}`, init)

  let json: any
  try {
    json = await response.json()
  } catch {
    throw new ApiError(
      `Non-JSON response from server (status ${response.status})`,
      'PARSE_ERROR',
      response.status,
    )
  }

  // Handle 401 Unauthorized / Token Expired
  const isAuthError = 
    response.status === 401 || 
    json?.errorCode === 'UNAUTHORIZED' || 
    (typeof json?.message === 'string' && json.message.toLowerCase().includes('jwt'))

  if (isAuthError) {
    // Attempt automatic refresh once if this isn't an auth path and not already a retry
    if (!isRetry && !path.startsWith('/auth/')) {
      const refreshedToken = await refreshAccessToken()
      if (refreshedToken) {
        return request<T>(method, path, body, true)
      }
    }

    setAccessToken(null)
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('skyway:unauthorized'))
    }

    throw new ApiError(
      'Session expired or authentication required. Please sign in.',
      'UNAUTHORIZED',
      401,
    )
  }

  if (!response.ok) {
    const err = json as ApiErrorResponse
    throw new ApiError(
      err.message ?? `HTTP ${response.status}`,
      err.errorCode ?? 'UNKNOWN_ERROR',
      response.status,
    )
  }

  const envelope = json as ApiResponse<T>
  return envelope.data
}

// ---------------------------------------------------------------------------
// Public API surface
// ---------------------------------------------------------------------------

export const api = {
  get<T>(path: string): Promise<T> {
    return request<T>('GET', path)
  },

  post<T>(path: string, body?: unknown): Promise<T> {
    return request<T>('POST', path, body)
  },

  put<T>(path: string, body?: unknown): Promise<T> {
    return request<T>('PUT', path, body)
  },

  delete<T>(path: string): Promise<T> {
    return request<T>('DELETE', path)
  },

  async login(payload: LoginRequest): Promise<AuthResponse> {
    const res = await request<AuthResponse>('POST', '/auth/login', payload)
    if (res?.accessToken) {
      setAccessToken(res.accessToken)
      if (typeof window !== 'undefined' && res.user) {
        localStorage.setItem('skyway_user', JSON.stringify(res.user))
      }
    }
    return res
  },

  async register(payload: RegisterRequest): Promise<AuthResponse> {
    const res = await request<AuthResponse>('POST', '/auth/register', payload)
    if (res?.accessToken) {
      setAccessToken(res.accessToken)
      if (typeof window !== 'undefined' && res.user) {
        localStorage.setItem('skyway_user', JSON.stringify(res.user))
      }
    }
    return res
  },

  async logout(): Promise<void> {
    try {
      await request<void>('POST', '/auth/logout')
    } catch {
      // Even if network error, clear local tokens
    } finally {
      setAccessToken(null)
      if (typeof window !== 'undefined') {
        localStorage.removeItem('skyway_user')
      }
    }
  },

  async getMyPassengers(): Promise<PassengerResponse[]> {
    return request<PassengerResponse[]>('GET', '/passengers/my')
  },

  async createPassenger(payload: PassengerRequest): Promise<PassengerResponse> {
    return request<PassengerResponse>('POST', '/passengers', payload)
  },

  async updatePassenger(id: number, payload: PassengerRequest): Promise<PassengerResponse> {
    return request<PassengerResponse>('PUT', `/passengers/${id}`, payload)
  },

  async deletePassenger(id: number): Promise<void> {
    return request<void>('DELETE', `/passengers/${id}`)
  },

  async getMyBookings(page = 0, size = 50): Promise<PageResponse<BookingResponse>> {
    return request<PageResponse<BookingResponse>>('GET', `/bookings/my?page=${page}&size=${size}`)
  },

  async getBookingById(id: number): Promise<BookingResponse> {
    return request<BookingResponse>('GET', `/bookings/${id}`)
  },

  async cancelBooking(id: number): Promise<void> {
    return request<void>('POST', `/bookings/${id}/cancel`, {})
  },
}
