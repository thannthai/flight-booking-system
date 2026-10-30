'use client'

import { AlertCircle, Calendar, Users } from 'lucide-react'
import { documentTypeInput, guardianIndexInput, type PassengerSlot } from '@/lib/checkout-model'
import type { PassengerFormController } from './use-passenger-form'
type Props = Pick<PassengerFormController, 'passengers' | 'paxList' | 'isAuthenticated' | 'errors' | 'handlePaxChange'> & {
  index: number
  pax: PassengerSlot
  saveToDirectory: boolean
  onSaveToDirectoryChange: (checked: boolean) => void
  onOpenDirectory: () => void
}
export function PassengerFields({ passengers, paxList, isAuthenticated, errors, handlePaxChange, index, pax, saveToDirectory, onSaveToDirectoryChange, onOpenDirectory }: Props) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white shadow-sm overflow-hidden">
      <div className="bg-slate-50 px-6 py-4 border-b border-slate-100 flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <h3 className="font-bold text-[#102a56] text-lg">
            {pax.title} <span className="font-normal text-slate-400 mx-2">|</span> {pax.type}
          </h3>
          {pax.type === 'Infant' && (
            <span className="bg-slate-200 text-slate-600 px-3 py-1 rounded-full text-xs font-bold">
              On Adult&apos;s Lap
            </span>
          )}
        </div>

        {isAuthenticated && (
          <button
            type="button"
            onClick={onOpenDirectory}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200 bg-white text-xs font-bold text-[#102a56] hover:bg-slate-100 hover:border-slate-300 transition-all cursor-pointer shadow-2xs"
          >
            <Users className="size-3.5 text-blue-600" />
            <span>Select from Directory</span>
          </button>
        )}
      </div>

      <div className="p-6">
        {/* Row 1: Full Name (50%) | DOB (50%) */}
        <div className="grid gap-6 md:grid-cols-2 mb-6">
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-2">Full name (as on ID) <span className="text-red-500 font-bold ml-0.5">*</span></label>
            <div className="flex gap-3">
              <select
                value={passengers[index].title}
                onChange={e => handlePaxChange(index, 'title', e.target.value)}
                className="w-20 h-[46px] rounded-xl border border-slate-200 px-2 text-sm focus:border-[#2563eb] outline-none bg-white cursor-pointer"
              >
                {pax.type === 'Adult' ? (
                  <>
                    <option>Mr</option>
                    <option>Ms</option>
                  </>
                ) : (
                  <>
                    <option>Mstr</option>
                    <option>Miss</option>
                  </>
                )}
              </select>
              <div className="flex-1">
                <input
                  id={`pax_${index}_name`}
                  type="text"
                  placeholder="e.g. NGUYEN VAN A"
                  value={passengers[index].name}
                  onChange={e => handlePaxChange(index, 'name', e.target.value.toUpperCase())}
                  className={`w-full h-[46px] rounded-xl border px-4 text-sm outline-none uppercase transition-all ${
                    errors[`pax_${index}_name`]
                      ? 'border-red-500 bg-red-50/20 focus:border-red-500 focus:ring-1 focus:ring-red-500'
                      : 'border-slate-200 focus:border-[#2563eb] focus:ring-1 focus:ring-[#2563eb]'
                  }`}
                />
              </div>
            </div>
            {errors[`pax_${index}_name`] ? (
              <p className="text-xs text-red-500 font-semibold mt-1.5 flex items-center gap-1">
                <AlertCircle className="size-3.5 shrink-0" /> {errors[`pax_${index}_name`]}
              </p>
            ) : (
              <p className="text-[10px] text-slate-400 mt-1.5 ml-1 uppercase tracking-wide">Without accents, as on government ID</p>
            )}
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-2">Date of birth <span className="text-red-500 font-bold ml-0.5">*</span></label>
            <div className="relative">
              <input
                id={`pax_${index}_dob`}
                type="date"
                max={new Date().toISOString().split('T')[0]}
                min="1900-01-01"
                value={passengers[index].dob}
                onChange={e => handlePaxChange(index, 'dob', e.target.value)}
                className={`w-full h-[46px] rounded-xl border px-4 text-sm outline-none appearance-none cursor-pointer transition-all ${
                  errors[`pax_${index}_dob`]
                    ? 'border-red-500 bg-red-50/20 focus:border-red-500 focus:ring-1 focus:ring-red-500'
                    : 'border-slate-200 focus:border-[#2563eb]'
                }`}
              />
              <Calendar className="absolute right-4 top-1/2 -translate-y-1/2 size-4 text-slate-400 pointer-events-none" />
            </div>
            {errors[`pax_${index}_dob`] && (
              <p className="text-xs text-red-500 font-semibold mt-1.5 flex items-center gap-1">
                <AlertCircle className="size-3.5 shrink-0" /> {errors[`pax_${index}_dob`]}
              </p>
            )}
          </div>
        </div>

        {/* Row 2: ID/Passport (50%) & Nationality (50%) */}
        <div className="grid gap-6 md:grid-cols-2">
          <div>
            <label className="flex items-center gap-1 text-xs font-bold text-slate-700 mb-2">
              National ID / Passport
              {pax.type === 'Adult' ? (
                <span className="text-red-500 font-bold ml-0.5">*</span>
              ) : (
                <span className="font-normal text-slate-400 italic">(Optional for {pax.type})</span>
              )}
            </label>
            <div className="flex gap-3">
              <select
                value={passengers[index].idType || ''}
                onChange={e => handlePaxChange(index, 'idType', documentTypeInput(e.target.value))}
                className="w-[120px] h-[46px] rounded-xl border border-slate-200 px-3 text-sm focus:border-[#2563eb] outline-none bg-white cursor-pointer"
              >
                {pax.type !== 'Adult' && <option value="">No document</option>}
                <option value="CITIZEN_ID">Citizen ID</option>
                <option value="PASSPORT">Passport</option>
              </select>
              <input
                id={`pax_${index}_id`}
                type="text"
                placeholder="Enter ID number"
                value={passengers[index].id}
                onChange={e => handlePaxChange(index, 'id', e.target.value)}
                className={`flex-1 h-[46px] rounded-xl border px-4 text-sm outline-none transition-all ${
                  errors[`pax_${index}_id`]
                    ? 'border-red-500 bg-red-50/20 focus:border-red-500 focus:ring-1 focus:ring-red-500'
                    : 'border-slate-200 focus:border-[#2563eb] focus:ring-1 focus:ring-[#2563eb]'
                }`}
              />
            </div>
            {errors[`pax_${index}_id`] && (
              <p className="text-xs text-red-500 font-semibold mt-1.5 flex items-center gap-1">
                <AlertCircle className="size-3.5 shrink-0" /> {errors[`pax_${index}_id`]}
              </p>
            )}
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-2">
              Nationality <span className="text-red-500 font-bold ml-0.5">*</span>
            </label>
            <select
              value={passengers[index].nationality || 'Vietnam'}
              onChange={e => handlePaxChange(index, 'nationality', e.target.value)}
              className="w-full h-[46px] rounded-xl border border-slate-200 px-4 text-sm focus:border-[#2563eb] outline-none bg-white cursor-pointer"
            >
              <option value="Vietnam">Vietnam</option>
              <option value="United States">United States</option>
              <option value="United Kingdom">United Kingdom</option>
              <option value="Australia">Australia</option>
              <option value="Japan">Japan</option>
              <option value="South Korea">South Korea</option>
              <option value="Singapore">Singapore</option>
              <option value="Malaysia">Malaysia</option>
              <option value="Thailand">Thailand</option>
              <option value="China">China</option>
              <option value="France">France</option>
              <option value="Germany">Germany</option>
              <option value="Canada">Canada</option>
              <option value="Other">Other</option>
            </select>
          </div>
        </div>

        {/* Row 3: Passport Expiry Date (Conditional when PASSPORT is selected) */}
        {passengers[index].idType === 'PASSPORT' && (
          <div className="mt-5 pt-5 border-t border-slate-100 grid gap-6 md:grid-cols-2 animate-in fade-in slide-in-from-top-2 duration-300">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-2">
                Passport Expiry Date (optional)
              </label>
              <div className="relative">
                <input
                  id={`pax_${index}_idExpiry`}
                  type="date"
                  value={passengers[index].idExpiry || ''}
                  onChange={e => handlePaxChange(index, 'idExpiry', e.target.value)}
                  className={`w-full h-[46px] rounded-xl border px-4 text-sm outline-none appearance-none cursor-pointer transition-all ${
                    errors[`pax_${index}_idExpiry`]
                      ? 'border-red-500 bg-red-50/20 focus:border-red-500 focus:ring-1 focus:ring-red-500'
                      : 'border-slate-200 focus:border-[#2563eb]'
                  }`}
                />
                <Calendar className="absolute right-4 top-1/2 -translate-y-1/2 size-4 text-slate-400 pointer-events-none" />
              </div>
              {errors[`pax_${index}_idExpiry`] ? (
                <p className="text-xs text-red-500 font-semibold mt-1.5 flex items-center gap-1">
                  <AlertCircle className="size-3.5 shrink-0" /> {errors[`pax_${index}_idExpiry`]}
                </p>
              ) : (
                <p className="text-[10px] text-slate-400 mt-1.5 ml-1">Must be valid for travel at departure date</p>
              )}
            </div>
          </div>
        )}

        {/* Infant Guardian Selection */}
        {pax.type === 'Infant' && (
          <div className="mt-5 pt-5 border-t border-slate-100">
            <label className="block text-xs font-bold text-slate-700 mb-2">
              Accompanying Adult (Guardian on Lap) <span className="text-red-500 font-bold ml-0.5">*</span>
            </label>
            <select
              id={`pax_${index}_guardian`}
              value={passengers[index].guardianIndex ?? 0}
              onChange={e => handlePaxChange(index, 'guardianIndex', guardianIndexInput(e.target.value))}
              className={`w-full md:w-1/2 h-[46px] rounded-xl border px-4 text-sm focus:border-[#2563eb] outline-none bg-white cursor-pointer transition-all ${
                errors[`pax_${index}_guardian`]
                  ? 'border-red-500 bg-red-50/20'
                  : 'border-slate-200'
              }`}
            >
              {paxList.map((p, aIdx) => {
                if (p.type !== 'Adult') return null;
                const aName = passengers[aIdx]?.name || `Adult ${aIdx + 1}`;
                return (
                  <option key={aIdx} value={aIdx}>
                    {p.title}: {aName}
                  </option>
                );
              })}
            </select>
            {errors[`pax_${index}_guardian`] && (
              <p className="text-xs text-red-500 font-semibold mt-1.5 flex items-center gap-1">
                <AlertCircle className="size-3.5 shrink-0" /> {errors[`pax_${index}_guardian`]}
              </p>
            )}
            <p className="text-[10px] text-slate-400 mt-1.5">
              Lap infants must be accompanied by an adult. 1 infant per adult.
            </p>
          </div>
        )}

        {/* Auto-save to directory option */}
        {isAuthenticated && (
          <div className="mt-5 pt-4 border-t border-slate-100">
            <label className="flex items-center gap-2.5 cursor-pointer w-fit group">
              <input
                type="checkbox"
                checked={saveToDirectory}
                onChange={e => onSaveToDirectoryChange(e.target.checked)}
                className="size-4 rounded border-slate-300 text-[#102a56] focus:ring-[#102a56] cursor-pointer"
              />
              <span className="text-xs font-semibold text-slate-600 group-hover:text-slate-900 transition-colors">
                Save this passenger to directory for faster booking next time
              </span>
            </label>
          </div>
        )}
      </div>
    </section>
  )
}
