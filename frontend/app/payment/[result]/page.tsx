'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { useAuth } from '@/lib/auth-context'
import { VietQRPayment } from '@/components/payment/VietQRPayment'
import { Button } from '@/components/ui/button'
import { paymentReturnBookingId } from '@/lib/deployment.mjs'

// Both success and cancel redirects are navigation hints. Only backend state confirms a payment.
export default function PaymentReturn() {
  const { isAuthenticated, isLoading, openAuthModal } = useAuth()
  const [id, setId] = useState<number | null>(null)
  const [ready, setReady] = useState(false)
  useEffect(() => { setId(paymentReturnBookingId(new URL(window.location.href).searchParams.get('bookingId'))); setReady(true) }, [])
  if (!ready || isLoading) return <main className="p-8" role="status">Loading payment status...</main>
  if (!id) return <main className="p-8"><p role="alert">Invalid booking reference.</p><Link href="/">Back to Home</Link></main>
  if (!isAuthenticated) return <main className="p-8 space-y-4"><p>Sign in to check this booking.</p><Button onClick={() => openAuthModal('login')}>Sign In</Button></main>
  return <VietQRPayment bookingId={id} onHome={() => window.location.assign('/')} />
}
