'use client'

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { api, getAccessToken, getCurrentUser, isTokenExpired, refreshAccessToken } from '@/lib/api'
import type { UserResponse, LoginRequest, RegisterRequest, AuthResponse } from '@/types/api'

interface AuthContextType {
  user: UserResponse | null
  token: string | null
  isAuthenticated: boolean
  isLoading: boolean
  isAuthModalOpen: boolean
  authModalMode: 'login' | 'register'
  login: (req: LoginRequest) => Promise<AuthResponse>
  register: (req: RegisterRequest) => Promise<AuthResponse>
  logout: () => Promise<void>
  openAuthModal: (mode?: 'login' | 'register', onSuccess?: () => void) => void
  closeAuthModal: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [isAuthModalOpen, setIsAuthModalOpen] = useState<boolean>(false)
  const [authModalMode, setAuthModalMode] = useState<'login' | 'register'>('login')
  const [onSuccessCallback, setOnSuccessCallback] = useState<(() => void) | null>(null)

  // Initialize auth state from storage on mount
  useEffect(() => {
    async function initAuth() {
      try {
        const storedUser = getCurrentUser()
        const currentToken = getAccessToken()

        if (currentToken && !isTokenExpired(currentToken)) {
          setToken(currentToken)
          setUser(storedUser)
        } else {
          // Token is missing or expired, attempt refresh via HttpOnly cookie
          const newToken = await refreshAccessToken()
          if (newToken) {
            setToken(newToken)
            setUser(getCurrentUser())
          } else {
            setToken(null)
            setUser(null)
          }
        }
      } catch (err) {
        console.warn('Auth initialization error:', err)
      } finally {
        setIsLoading(false)
      }
    }

    initAuth()
  }, [])

  // Listen for 401 unauthorized events from api.ts
  useEffect(() => {
    const handleUnauthorized = () => {
      setUser(null)
      setToken(null)
      setIsAuthModalOpen(true)
      setAuthModalMode('login')
    }

    window.addEventListener('skyway:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('skyway:unauthorized', handleUnauthorized)
  }, [])

  const openAuthModal = useCallback((mode: 'login' | 'register' = 'login', onSuccess?: () => void) => {
    setAuthModalMode(mode)
    if (onSuccess) {
      setOnSuccessCallback(() => onSuccess)
    } else {
      setOnSuccessCallback(null)
    }
    setIsAuthModalOpen(true)
  }, [])

  const closeAuthModal = useCallback(() => {
    setIsAuthModalOpen(false)
    setOnSuccessCallback(null)
  }, [])

  const login = useCallback(async (req: LoginRequest): Promise<AuthResponse> => {
    setIsLoading(true)
    try {
      const res = await api.login(req)
      setToken(res.accessToken)
      setUser(res.user)
      setIsAuthModalOpen(false)
      
      // If there was a pending action waiting for login (e.g. hold seats)
      if (onSuccessCallback) {
        setTimeout(() => {
          onSuccessCallback()
          setOnSuccessCallback(null)
        }, 100)
      }
      return res
    } finally {
      setIsLoading(false)
    }
  }, [onSuccessCallback])

  const register = useCallback(async (req: RegisterRequest): Promise<AuthResponse> => {
    setIsLoading(true)
    try {
      const res = await api.register(req)
      setToken(res.accessToken)
      setUser(res.user)
      setIsAuthModalOpen(false)
      
      if (onSuccessCallback) {
        setTimeout(() => {
          onSuccessCallback()
          setOnSuccessCallback(null)
        }, 100)
      }
      return res
    } finally {
      setIsLoading(false)
    }
  }, [onSuccessCallback])

  const logout = useCallback(async () => {
    setIsLoading(true)
    try {
      await api.logout()
    } finally {
      setToken(null)
      setUser(null)
      setIsLoading(false)
    }
  }, [])

  const isAuthenticated = !!user && !!token && !isTokenExpired(token)

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated,
        isLoading,
        isAuthModalOpen,
        authModalMode,
        login,
        register,
        logout,
        openAuthModal,
        closeAuthModal,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
