'use client'

import { useState } from 'react'
import { Building2, Copy, Check } from 'lucide-react'
import type { PaymentResponse } from '@/types/api'
import { formatVND } from '@/lib/format'
import { bankName } from '@/lib/banks'

export function TransferDetails({ payment }: { payment: PaymentResponse }) {
  const [copied, setCopied] = useState('')
  const [message, setMessage] = useState('')
  const demo = payment.provider === 'DEMO'
  const amount = demo ? payment.amount : payment.transferAmount ?? payment.amount
  const copy = async (label: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(label)
      setMessage(`${label} copied`)
    } catch {
      setCopied('')
      setMessage('Could not copy. Please select and copy the value manually.')
    }
  }
  const copyButton = (label: string, value?: string | null) => <button
    type="button" aria-label={`Copy ${label.toLowerCase()}`} disabled={!value}
    onClick={() => { if (value) void copy(label, value) }}
    className="group absolute right-0 top-1/2 inline-flex min-h-11 min-w-11 -translate-y-1/2 cursor-pointer items-center justify-center rounded-md px-1 text-[11px] font-medium text-slate-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-default disabled:opacity-30"
  ><span className="inline-flex h-7 items-center gap-1 rounded-md bg-slate-100 px-2 transition-colors group-hover:bg-slate-200">{copied === label ? <Check aria-hidden="true" className="size-3 text-emerald-700" /> : <Copy aria-hidden="true" className="size-3" />}<span>{copied === label ? 'Copied' : 'Copy'}</span></span></button>
  const rows = [
    { label: 'Bank', value: bankName(payment.bankBin), bank: true },
    { label: 'Account holder', value: payment.accountName },
    { label: 'Account number', value: payment.accountNumber, copy: true },
    { label: 'Total amount', value: amount != null ? formatVND(amount) : null, copy: true, amount: true },
    { label: 'Transfer description', value: payment.transferDescription, copy: true },
  ]
  return <section aria-label="Transfer information" className="space-y-3">
    {demo ? <>
      <dl className="flex items-center justify-between gap-4"><dt className="text-sm text-slate-600">Total amount · Demo</dt><dd className="text-2xl font-bold text-[#102a56]">{amount != null ? formatVND(amount) : 'Unavailable'}</dd></dl>
      <p className="text-sm leading-relaxed text-slate-600">Demo confirmation only; no money is transferred.</p>
    </> : <dl className="divide-y divide-slate-200">
      {rows.map(row => <div key={row.label} className={`relative min-w-0 py-3 first:pt-0 last:pb-0 ${row.copy ? 'min-h-[68px] pr-24' : ''}`}>
          <dt className="text-xs text-slate-500">{row.label}</dt>
          <dd className={`mt-1 flex min-w-0 items-center gap-2 font-semibold ${row.amount ? 'text-xl text-[#102a56]' : 'text-sm text-slate-900'}`}>
            {row.bank && <Building2 aria-hidden="true" className="size-4 shrink-0 text-slate-500" />}
            <span className="min-w-0 select-text break-words [overflow-wrap:anywhere]">{row.value || 'Unavailable'}</span>
            {row.copy && copyButton(row.amount ? 'Transfer amount' : row.label, row.amount ? payment.transferAmount != null ? String(payment.transferAmount) : null : row.value)}
          </dd>
      </div>)}
    </dl>}
    {!demo && (rows.some(row => !row.value) || payment.transferAmount == null) && <p className="text-xs leading-relaxed text-slate-500">Some bank details are unavailable for this payment. Use the existing QR or official payOS checkout.</p>}
    <p role="status" aria-live="polite" className={message ? 'text-xs text-emerald-700' : 'sr-only'}>{message}</p>
  </section>
}
