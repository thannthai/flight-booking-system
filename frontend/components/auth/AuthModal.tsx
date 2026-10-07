'use client'

import React, { useState, useEffect } from 'react'
import { useAuth } from '@/lib/auth-context'
import { 
  X, 
  Mail, 
  Lock, 
  User, 
  Phone, 
  Eye, 
  EyeOff, 
  Plane, 
  Sparkles, 
  AlertCircle, 
  CheckCircle2, 
  Loader2 
} from 'lucide-react'

export function AuthModal() {
  const { 
    isAuthModalOpen, 
    authModalMode, 
    closeAuthModal, 
    openAuthModal, 
    login, 
    register 
  } = useAuth()

  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Login form state
  const [loginEmail, setLoginEmail] = useState('')
  const [loginPassword, setLoginPassword] = useState('')

  // Register form state
  const [regFullName, setRegFullName] = useState('')
  const [regEmail, setRegEmail] = useState('')
  const [regPhone, setRegPhone] = useState('')
  const [regPassword, setRegPassword] = useState('')

  // Sync mode with context and reset state when opening
  useEffect(() => {
    if (authModalMode) {
      setMode(authModalMode)
    }
    if (isAuthModalOpen) {
      setError(null)
      setLoginEmail('')
      setLoginPassword('')
      setRegFullName('')
      setRegEmail('')
      setRegPhone('')
      setRegPassword('')
      setShowPassword(false)
    }
  }, [authModalMode, isAuthModalOpen])

  if (!isAuthModalOpen) return null

  // Password rules validation for register
  const hasMinLength = regPassword.length >= 8
  const hasUpper = /[A-Z]/.test(regPassword)
  const hasLower = /[a-z]/.test(regPassword)
  const hasDigit = /[0-9]/.test(regPassword)
  const hasSpecial = /[@#$%^&+=!]/.test(regPassword)
  const isPasswordValid = hasMinLength && hasUpper && hasLower && hasDigit && hasSpecial

  const handleFillDemo = () => {
    setLoginEmail('skyway_tester@example.com')
    setLoginPassword('Password123@')
    setError(null)
  }

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!loginEmail.trim() || !loginPassword) {
      setError('Please enter both email and password.')
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await login({
        email: loginEmail.trim(),
        password: loginPassword,
      })
      // Modal is closed by login()
    } catch (err: any) {
      const msg = err.message || 'Login failed. Please check your credentials.'
      if (msg.includes('Bad credentials') || err.errorCode === 'INVALID_CREDENTIALS') {
        setError('Incorrect email or password. Please try again.')
      } else {
        setError(msg)
      }
    } finally {
      setSubmitting(false)
    }
  }

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!regFullName.trim()) {
      setError('Please enter your full name.')
      return
    }
    if (!regEmail.trim()) {
      setError('Please enter your email.')
      return
    }
    if (!regPhone.trim()) {
      setError('Please enter your phone number.')
      return
    }
    if (!isPasswordValid) {
      setError('Please meet all password requirements before continuing.')
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await register({
        fullName: regFullName.trim(),
        email: regEmail.trim(),
        phone: regPhone.trim(),
        password: regPassword,
      })
    } catch (err: any) {
      setError(err.message || 'Registration failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200"
      onClick={(e) => {
        if (e.target === e.currentTarget) closeAuthModal()
      }}
      role="dialog"
      aria-modal="true"
    >
      <div className="relative w-full max-w-md rounded-3xl bg-white p-7 shadow-2xl border border-slate-100 transition-all overflow-hidden">
        {/* Close Button */}
        <button
          onClick={closeAuthModal}
          className="absolute right-5 top-5 flex size-9 items-center justify-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors cursor-pointer"
          aria-label="Close"
        >
          <X className="size-5" />
        </button>



        {/* Title */}
        <div className="mb-6">
          <h2 className="text-2xl font-bold text-slate-900 tracking-tight">
            {mode === 'login' ? 'Welcome back' : 'Create an account'}
          </h2>
        </div>

        {/* Tab Switcher */}
        <div className="grid grid-cols-2 p-1 mb-6 rounded-2xl bg-slate-100 text-sm font-semibold">
          <button
            type="button"
            onClick={() => { setMode('login'); setError(null) }}
            className={`py-2 rounded-xl transition-all cursor-pointer ${
              mode === 'login'
                ? 'bg-white text-[#102a56] shadow-sm font-bold'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setMode('register'); setError(null) }}
            className={`py-2 rounded-xl transition-all cursor-pointer ${
              mode === 'register'
                ? 'bg-white text-[#102a56] shadow-sm font-bold'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            Register
          </button>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="mb-5 flex items-start gap-2.5 rounded-2xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-600 font-medium animate-in fade-in duration-150">
            <AlertCircle className="size-4 shrink-0 text-red-500 mt-0.5" />
            <div className="leading-relaxed">{error}</div>
          </div>
        )}

        {/* LOGIN FORM */}
        {mode === 'login' ? (
          <form onSubmit={handleLoginSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Email Address
              </label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <input
                  type="email"
                  required
                  placeholder="name@example.com"
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 pl-10 pr-4 text-sm text-slate-900 transition-all focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider">
                  Password
                </label>
              </div>
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  placeholder="Enter your password"
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 pl-10 pr-10 text-sm text-slate-900 transition-all focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>

            {/* Quick Demo Account Button */}
            <div className="pt-1">
              <button
                type="button"
                onClick={handleFillDemo}
                className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-xl border border-amber-200/80 bg-amber-50/70 text-amber-900 hover:bg-amber-100/70 transition-all text-xs font-semibold cursor-pointer shadow-2xs"
              >
                <Sparkles className="size-3.5 text-amber-500" />
                <span>Quick fill demo account (1-click)</span>
              </button>
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="w-full mt-2 flex items-center justify-center gap-2 rounded-xl bg-[#102a56] py-3 text-sm font-bold text-white shadow-md shadow-blue-950/20 hover:bg-[#1a3d75] transition-all disabled:opacity-60 cursor-pointer"
            >
              {submitting ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  <span>Signing in...</span>
                </>
              ) : (
                'Sign In'
              )}
            </button>
          </form>
        ) : (
          /* REGISTER FORM */
          <form onSubmit={handleRegisterSubmit} className="space-y-3.5">
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                Full Name <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <User className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <input
                  type="text"
                  required
                  placeholder="e.g. Thai Chi Thanh"
                  value={regFullName}
                  onChange={(e) => setRegFullName(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2 pl-10 pr-4 text-sm text-slate-900 transition-all focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                  Email <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                  <input
                    type="email"
                    required
                    placeholder="name@email.com"
                    value={regEmail}
                    onChange={(e) => setRegEmail(e.target.value)}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2 pl-10 pr-3 text-xs text-slate-900 transition-all focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                  Phone <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <Phone className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                  <input
                    type="tel"
                    required
                    placeholder="0911223344"
                    value={regPhone}
                    onChange={(e) => setRegPhone(e.target.value)}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2 pl-10 pr-3 text-xs text-slate-900 transition-all focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                  />
                </div>
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                Password <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  placeholder="Min. 8 chars with uppercase, digit & symbol"
                  value={regPassword}
                  onChange={(e) => setRegPassword(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2 pl-10 pr-10 text-xs text-slate-900 transition-all focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={submitting || !isPasswordValid}
              className="w-full mt-4 flex items-center justify-center gap-2 rounded-xl bg-[#102a56] py-3 text-sm font-bold text-white shadow-md shadow-blue-950/20 hover:bg-[#1a3d75] transition-all disabled:opacity-50 cursor-pointer"
            >
              {submitting ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  <span>Creating account...</span>
                </>
              ) : (
                'Create Account'
              )}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
