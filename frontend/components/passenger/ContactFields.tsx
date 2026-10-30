'use client'

import { AlertCircle } from 'lucide-react'
import type { ContactForm } from '@/lib/checkout-model'
interface Props {
  contactInfo: ContactForm
  errors: Record<string, string>
  copyToPax1: boolean
  handleContactChange: (field: keyof ContactForm, value: string) => void
  handleCopyToggle: (checked: boolean) => void
}
export function ContactFields({ contactInfo, errors, copyToPax1, handleContactChange, handleCopyToggle }: Props) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-xl font-bold text-[#102a56]">Contact information</h2>
      <p className="mt-1 text-sm text-slate-500 mb-6">Your e-ticket and booking updates will be sent here.</p>

      <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
        <div>
          <label className="block text-xs font-bold text-slate-700 mb-2">Contact full name <span className="text-red-500 font-bold ml-0.5">*</span></label>
          <input
            id="contact_name"
            type="text"
            placeholder="e.g. NGUYEN VAN A"
            value={contactInfo.name}
            onChange={e => handleContactChange('name', e.target.value.toUpperCase())}
            className={`w-full h-[46px] rounded-xl border px-4 py-3 text-sm outline-none uppercase transition-all ${
              errors['contact_name']
                ? 'border-red-500 bg-red-50/20 focus:border-red-500 focus:ring-1 focus:ring-red-500'
                : 'border-slate-200 focus:border-[#2563eb] focus:ring-1 focus:ring-[#2563eb]'
            }`}
          />
          {errors['contact_name'] && (
            <p className="text-xs text-red-500 font-semibold mt-1.5 flex items-center gap-1">
              <AlertCircle className="size-3.5 shrink-0" /> {errors['contact_name']}
            </p>
          )}
        </div>
        <div>
          <label className="block text-xs font-bold text-slate-700 mb-2">Email address <span className="text-red-500 font-bold ml-0.5">*</span></label>
          <input
            id="contact_email"
            type="email"
            placeholder="e.g. email@example.com"
            value={contactInfo.email}
            onChange={e => handleContactChange('email', e.target.value)}
            className={`w-full h-[46px] rounded-xl border px-4 py-3 text-sm outline-none transition-all ${
              errors['contact_email']
                ? 'border-red-500 bg-red-50/20 focus:border-red-500 focus:ring-1 focus:ring-red-500'
                : 'border-slate-200 focus:border-[#2563eb] focus:ring-1 focus:ring-[#2563eb]'
            }`}
          />
          {errors['contact_email'] && (
            <p className="text-xs text-red-500 font-semibold mt-1.5 flex items-center gap-1">
              <AlertCircle className="size-3.5 shrink-0" /> {errors['contact_email']}
            </p>
          )}
        </div>
        <div>
          <label className="block text-xs font-bold text-slate-700 mb-2">Phone number <span className="text-red-500 font-bold ml-0.5">*</span></label>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-sm font-bold text-slate-500">+84</span>
            <input
              id="contact_phone"
              type="tel"
              placeholder="901234567"
              value={contactInfo.phone}
              onChange={e => handleContactChange('phone', e.target.value)}
              className={`w-full h-[46px] rounded-xl border pl-12 pr-4 py-3 text-sm outline-none transition-all ${
                errors['contact_phone']
                  ? 'border-red-500 bg-red-50/20 focus:border-red-500 focus:ring-1 focus:ring-red-500'
                  : 'border-slate-200 focus:border-[#2563eb] focus:ring-1 focus:ring-[#2563eb]'
              }`}
            />
          </div>
          {errors['contact_phone'] && (
            <p className="text-xs text-red-500 font-semibold mt-1.5 flex items-center gap-1">
              <AlertCircle className="size-3.5 shrink-0" /> {errors['contact_phone']}
            </p>
          )}
        </div>
      </div>

      <label className="flex items-center gap-3 mt-5 cursor-pointer w-fit group">
        <input
          type="checkbox"
          checked={copyToPax1}
          onChange={e => handleCopyToggle(e.target.checked)}
          className="size-4 rounded border-slate-300 text-[#102a56] focus:ring-[#102a56] cursor-pointer"
        />
        <span className="text-sm font-semibold text-slate-700 group-hover:text-slate-900 transition-colors">
          I am also a passenger (Autofill for Passenger 1)
        </span>
      </label>
    </section>
  )
}
