'use client'

import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'

export function DemoGuide() {
  const { loginDemo, isAuthenticated, isLoading } = useAuth()
  const [enabled, setEnabled] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    api.get<{ enabled: boolean; accountsEnabled?: boolean }>('/demo/info')
      .then(info => { if (active) setEnabled(info.enabled && !!info.accountsEnabled) }).catch(() => {})
    return () => { active = false }
  }, [])
  if (!enabled || isAuthenticated) return null
  const login = async () => {
    setBusy(true); setError('')
    try { await loginDemo('A') }
    catch { setError('Demo sign-in is unavailable. Please retry.') }
    finally { setBusy(false) }
  }
  return <>
    <button type="button" disabled={busy || isLoading} onClick={() => void login()} className="cursor-pointer rounded-full border border-blue-200 bg-blue-50 px-4 py-2 text-xs font-bold text-[#102a56] disabled:opacity-50">{busy ? 'Signing in…' : 'Try demo'}</button>
    {error && <span role="alert" className="text-xs text-red-700">{error}</span>}
  </>
}
