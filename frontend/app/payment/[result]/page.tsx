'use client'

import Link from 'next/link'
import { Suspense } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useAuth } from '@/lib/auth-context'
import { VietQRPayment } from '@/components/payment/VietQRPayment'
import { Button } from '@/components/ui/button'
import { paymentReturnBookingId } from '@/lib/deployment.mjs'

// Both success and cancel redirects are navigation hints. Only backend state confirms a payment.
export default function PaymentReturn() {
  return <Suspense fallback={<main className="p-8" role="status">Loading payment status...</main>}><PaymentReturnContent /></Suspense>
}

function PaymentReturnContent() {
  const { isAuthenticated, isLoading, openAuthModal } = useAuth()
  const query = useSearchParams()
  const router = useRouter()
  const id = paymentReturnBookingId(query.get('bookingId'))
  if (isLoading) return <main className="p-8" role="status">Loading payment status...</main>
  if (!id) return <main className="p-8"><p role="alert">Invalid booking reference.</p><Link href="/">Back to Home</Link></main>
  if (!isAuthenticated) return <main className="p-8 space-y-4"><p>Sign in to check this booking.</p><Button onClick={() => openAuthModal('login')}>Sign In</Button></main>
  return <VietQRPayment bookingId={id} onHome={() => router.push('/')} />
}
