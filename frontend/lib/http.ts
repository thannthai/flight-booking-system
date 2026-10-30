import type { ApiResponse, ApiErrorResponse } from '@/types/api'

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

export function getBaseUrl(): string {
  const url = process.env.NEXT_PUBLIC_API_URL ?? '/api/v1'
  if (url.replace(/\/$/, '') !== '/api/v1') throw new Error('NEXT_PUBLIC_API_URL must be /api/v1; configure server-only BACKEND_API_URL for the proxy.')
  return '/api/v1'
}

export async function fetchWithTimeout(input: string, init: RequestInit, timeoutMs = 20000): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try { return await fetch(input, { ...init, signal: controller.signal }) }
  finally { clearTimeout(timer) }
}

export type ApiRequest = <T>(method: string, path: string, body?: unknown) => Promise<T>

export async function requestJson<T>(method: string, path: string, body?: unknown, token?: string | null, assertCurrent: () => void = () => {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const init: RequestInit = { method, headers, credentials: 'include', cache: 'no-store' }
  if (body !== undefined) init.body = JSON.stringify(body)
  const response = await fetchWithTimeout(`${getBaseUrl()}${path}`, init)
  assertCurrent()
  let json: ApiResponse<T> & Partial<ApiErrorResponse>
  try { json = await response.json() }
  catch { throw new ApiError(`Non-JSON response from server (status ${response.status})`, 'PARSE_ERROR', response.status) }
  assertCurrent()
  if (!response.ok) throw new ApiError(json.message ?? (response.status === 401 ? 'Authentication failed' : `HTTP ${response.status}`), json.errorCode ?? (response.status === 401 ? 'UNAUTHORIZED' : 'UNKNOWN_ERROR'), response.status)
  return json.data
}
