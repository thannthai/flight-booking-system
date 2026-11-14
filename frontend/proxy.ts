import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'
import { backendHeaders } from '@/lib/client-ip.mjs'

// The /api/v1 rewrite hides the visitor's address from the backend, which would then count every
// visitor as one client. Pass the address Vercel reports, authenticated with a secret shared with the backend.
export function proxy(request: NextRequest) {
  const headers = backendHeaders(request.headers, process.env.RATE_LIMIT_PROXY_SECRET)
  return NextResponse.next({ request: { headers } })
}

export const config = { matcher: '/api/v1/:path*' }
