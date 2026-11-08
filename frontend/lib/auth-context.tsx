'use client'

import { createContext, useCallback, useContext, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { api, getAuthState, getServerAuthState, subscribeAuth, isTokenExpired, refreshAccessToken, invalidateAuth } from '@/lib/api'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '@/types/api'

interface AuthContextType {
  user: UserResponse | null
  token: string | null
  isAuthenticated: boolean
  isLoading: boolean
  authError: string | null
  isAuthModalOpen: boolean
  authModalMode: 'login' | 'register'
  loginDemo: (alias: 'A' | 'B') => Promise<AuthResponse>
  login: (request: LoginRequest) => Promise<AuthResponse>
  register: (request: RegisterRequest) => Promise<AuthResponse>
  logout: () => Promise<void>
  openAuthModal: (mode?: 'login' | 'register', onSuccess?: () => void) => void
  closeAuthModal: () => void
}
const AuthContext = createContext<AuthContextType | undefined>(undefined)
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { token, user } = useSyncExternalStore(subscribeAuth, getAuthState, getServerAuthState)
  const [authError, setAuthError] = useState<string | null>(null)
  const [isLoading, setLoading] = useState(true)
  const [isAuthModalOpen, setOpen] = useState(false)
  const [authModalMode, setMode] = useState<'login' | 'register'>('login')
  const pending = useRef<(() => void) | null>(null)
  useEffect(() => {
    let active = true
    // Remove legacy persisted tokens/user caches; cookie is the authority for restoring auth.
    try { sessionStorage.removeItem('access_token'); localStorage.removeItem('skyway_user') } catch { /* Storage may be disabled. */ }
    void refreshAccessToken().finally(() => { if (active) setLoading(false) })
    const unauthorized = () => { pending.current = null; setMode('login'); setOpen(true) }
    const storage = (event: StorageEvent) => {
      if (event.key !== 'skyway_auth_changed' || !event.newValue) return
      try {
        const action = JSON.parse(event.newValue).action
        pending.current = null
        invalidateAuth(action !== 'signin')
        if (action === 'signin') void refreshAccessToken()
      } catch { invalidateAuth(true) }
    }
    window.addEventListener('skyway:unauthorized', unauthorized)
    window.addEventListener('storage', storage)
    return () => { active = false; window.removeEventListener('skyway:unauthorized', unauthorized); window.removeEventListener('storage', storage) }
  }, [])
  useEffect(() => {
    if (!token) return
    const timer = setInterval(() => { if (isTokenExpired(token)) void refreshAccessToken() }, 10000)
    return () => clearInterval(timer)
  }, [token])
  const openAuthModal = useCallback((mode: 'login' | 'register' = 'login', action?: () => void) => {
    pending.current = action || null
    setMode(mode)
    setOpen(true)
  }, [])
  const closeAuthModal = useCallback(() => { setOpen(false); pending.current = null }, [])
  const finishAuth = async (request: Promise<AuthResponse>) => {
    try {
      const result = await request
      setOpen(false)
      setAuthError(null)
      const action = pending.current
      pending.current = null
      action?.()
      return result
    } finally { setLoading(false) }
  }
  return <AuthContext.Provider value={{ user, token, isAuthenticated: !!user && !!token && !isTokenExpired(token), isLoading, authError, isAuthModalOpen, authModalMode,
    loginDemo: alias => finishAuth(api.loginDemo(alias)),
    login: request => finishAuth(api.login(request)), register: request => finishAuth(api.register(request)),
    logout: async () => { pending.current = null; setOpen(false); try { await api.logout() } catch (error) { setAuthError('Signed out locally. Server sign-out could not be confirmed; reconnect before signing in again.'); throw error } }, openAuthModal, closeAuthModal }}>{children}</AuthContext.Provider>
}
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
