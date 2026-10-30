'use client'

import { useState } from 'react'
import { 
  Check, 
  Plane, 
  ArrowRight, 
  ArrowLeft, 
  AlertCircle,
  Users,
  Sparkles,
  Plus
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { departureDate } from '@/lib/passenger-validation'
import type { PassengerCategory } from '@/lib/checkout-model'
import { formatVND } from '@/lib/format'
import { UserNav } from '@/components/auth/UserNav'
import { PassengerDirectoryModal } from '@/components/passenger/PassengerDirectoryModal'
import type { AdaptedFlight } from '@/lib/flight'
import { usePassengerForm, type PassengerFormOptions } from './use-passenger-form'
import { ContactFields } from './ContactFields'
import { PassengerFields } from './PassengerFields'
import { PassengerAddOns } from './PassengerAddOns'

export function PassengerDetails(props: Omit<Parameters<typeof PassengerDetailsForm>[0], 'flight'> & { flight: AdaptedFlight | null }) {
  if (!props.flight || !props.searchParams) return <main className="p-8"><p>Choose a flight first.</p><Button onClick={props.onHome}>Start New Search</Button></main>
  return <PassengerDetailsForm {...props} flight={props.flight} />
}

function PassengerDetailsForm({
  flight, 
  returnFlight,
  searchParams,
  passengerData,
  onDraftChange,
  onBack, 
  onHome, 
  onContinue 
}: PassengerFormOptions & { onBack: () => void; onHome: () => void }) {
  const form = usePassengerForm({ flight, returnFlight, searchParams, passengerData, onDraftChange, onContinue })
  const {
    isAuthenticated, contactInfo, passengers, paxList, errors, showErrorBanner,
    copyToPax1, savedPassengers, uniqueSavedPassengers, saveToDirectory, setSaveToDirectory,
    passengerAddOns, activeAddOnPax, setActiveAddOnPax,
    totalBaseFare, totalTaxes, addOnsTotal, finalTotal,
    handleContactChange, handleCopyToggle, handlePaxChange, handleUpdateAddOn,
    handleApplyAddOnsToAll, handleQuickFillFirstAvailable, applyPassengerFromDirectory,
    handleContinue, refreshDirectory,
  } = form
  const [dirModalOpen, setDirModalOpen] = useState(false)
  const [selectingForPaxIndex, setSelectingForPaxIndex] = useState<number | null>(null)
  const [selectedTargetType, setSelectedTargetType] = useState<PassengerCategory | undefined>(undefined)

  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950 pb-24">
      {/* Header */}
      <header className="bg-white sticky top-0 z-40">
        <div className="border-b border-slate-200">
          <div className="mx-auto flex h-[72px] max-w-7xl items-center justify-between px-5 lg:px-8">
            <button onClick={onHome} className="flex items-center gap-3 cursor-pointer" aria-label="Skyway home">
              <span className="flex size-10 items-center justify-center rounded-xl bg-[#102a56] text-white">
                <Plane className="size-5 -rotate-12" />
              </span>
              <span className="text-xl font-bold tracking-tight">
                skyway<span className="text-[#f59e0b]">.</span>
              </span>
            </button>
            <div className="flex items-center gap-3">
              <UserNav />
            </div>
          </div>
        </div>
        
        {/* Sub-bar Stepper */}
        <div className="hidden lg:block border-b border-slate-200 bg-slate-50/50">
          <div className="mx-auto flex h-11 max-w-4xl items-center justify-center gap-4 text-sm font-semibold text-slate-400">
            <button onClick={onHome} className="flex items-center gap-2 text-emerald-600 font-bold hover:underline cursor-pointer">
              <Check className="size-4" /> Search
            </button>
            <span className="h-px w-8 bg-slate-300" />
            <button onClick={onBack} className="flex items-center gap-2 text-emerald-600 font-bold hover:underline cursor-pointer">
              <Check className="size-4" /> Flights
            </button>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2 text-[#102a56] font-bold">
              <div className="size-5 rounded-full bg-[#102a56] text-white flex items-center justify-center text-[10px] shadow-[0_0_0_3px_rgba(16,42,86,0.1)]">3</div> Passengers
            </span>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2"><div className="size-5 rounded-full border border-slate-300 flex items-center justify-center text-[10px]">4</div> Seats</span>
            <span className="h-px w-8 bg-slate-300" />
            <span className="flex items-center gap-2"><div className="size-5 rounded-full border border-slate-300 flex items-center justify-center text-[10px]">5</div> Payment</span>
          </div>
        </div>
      </header>

      {/* Selected Flight Bar */}
      <section className="bg-[#102a56] text-white border-b-4 border-[#f59e0b]">
        <div className="mx-auto max-w-7xl px-5 py-6 lg:px-8 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <button onClick={onBack} className="flex items-center gap-2 text-slate-300 hover:text-white text-[11px] font-bold uppercase tracking-widest mb-4 cursor-pointer transition-colors">
              <ArrowLeft className="size-3.5" /> Back to Flights
            </button>
            <p className="text-[10px] font-bold uppercase tracking-[.2em] text-slate-300/80 mb-3">
              {returnFlight ? 'Selected flights (Round-Trip)' : 'Selected flight'}
            </p>
            <div className="flex flex-col gap-3">
              {/* Outbound */}
              <div className="flex items-center gap-4">
                <img src={`/airlines/${flight.logo}.svg`} alt={flight.airline} className="h-9 w-14 object-contain bg-white rounded-lg p-1" />
                <div>
                  <p className="text-base font-bold text-white">
                    {returnFlight && <span className="text-[10px] px-2 py-0.5 rounded bg-blue-500/30 text-blue-200 mr-2 uppercase font-black tracking-wider">Outbound</span>}
                    {flight.airline} · {flight.code} 
                    <span className="ml-2 font-normal text-slate-300 text-xs hidden sm:inline-block">
                      {flight.aircraft}
                    </span>
                  </p>
                  <p className="text-xs text-slate-300 mt-0.5 flex items-center gap-1.5 flex-wrap">
                    <span className="font-semibold text-white">{searchParams.originCode || 'SGN'}</span> ➔ <span className="font-semibold text-white">{searchParams.destinationCode || 'HAN'}</span> 
                    <span className="opacity-50 mx-1">•</span> 
                    {flight.depart} - {flight.arrive} 
                    <span className="opacity-50 mx-1">•</span> 
                    {paxList.length} Passenger{paxList.length > 1 ? 's' : ''}
                  </p>
                </div>
              </div>
              {/* Return (if present) */}
              {returnFlight && (
                <div className="flex items-center gap-4 pt-2 border-t border-white/10">
                  <img src={`/airlines/${returnFlight.logo}.svg`} alt={returnFlight.airline} className="h-9 w-14 object-contain bg-white rounded-lg p-1" />
                  <div>
                    <p className="text-base font-bold text-white">
                      <span className="text-[10px] px-2 py-0.5 rounded bg-amber-500/30 text-amber-200 mr-2 uppercase font-black tracking-wider">Return</span>
                      {returnFlight.airline} · {returnFlight.code} 
                      <span className="ml-2 font-normal text-slate-300 text-xs hidden sm:inline-block">
                        {returnFlight.aircraft}
                      </span>
                    </p>
                    <p className="text-xs text-slate-300 mt-0.5 flex items-center gap-1.5 flex-wrap">
                      <span className="font-semibold text-white">{searchParams.destinationCode || 'HAN'}</span> ➔ <span className="font-semibold text-white">{searchParams.originCode || 'SGN'}</span> 
                      <span className="opacity-50 mx-1">•</span> 
                      {returnFlight.depart} - {returnFlight.arrive} 
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Main Content */}
      <div className="mx-auto grid max-w-7xl gap-8 px-5 py-8 lg:grid-cols-[1fr_360px] lg:px-8 items-start">
        
        <div className="flex flex-col gap-6">
          
          <ContactFields contactInfo={contactInfo} errors={errors} copyToPax1={copyToPax1} handleContactChange={handleContactChange} handleCopyToggle={handleCopyToggle} />

          {/* Quick suggestions from saved passenger directory */}
          {isAuthenticated && uniqueSavedPassengers.length > 0 && (
            <div className="rounded-2xl border border-blue-100 bg-gradient-to-r from-blue-50/70 to-indigo-50/40 p-4 mb-6 shadow-2xs">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="size-4 text-amber-500 shrink-0" />
                  <span className="text-xs font-bold text-[#102a56]">Quick suggestions from your directory:</span>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedTargetType(undefined)
                    setSelectingForPaxIndex(null)
                    setDirModalOpen(true)
                  }}
                  className="text-xs font-bold text-[#2563eb] hover:underline cursor-pointer flex items-center gap-1 self-start sm:self-auto"
                >
                  <Users className="size-3.5" />
                  <span>Manage Directory ({savedPassengers.length})</span>
                </button>
              </div>

              <div className="flex flex-wrap items-center gap-2 mt-3">
                {uniqueSavedPassengers.map((sp) => (
                  <button
                    key={sp.id}
                    type="button"
                    onClick={() => handleQuickFillFirstAvailable(sp)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-blue-200 bg-white text-xs font-bold text-slate-800 hover:bg-blue-50/80 hover:border-blue-300 transition-all cursor-pointer shadow-2xs group"
                  >
                    <Plus className="size-3 text-blue-600 group-hover:scale-110 transition-transform" />
                    <span className="uppercase">{sp.fullName}</span>
                    <span className="text-[10px] font-medium text-slate-400">
                      ({sp.identityType === 'PASSPORT' ? 'Passport' : 'ID'})
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {paxList.map((pax, index) => <PassengerFields
            key={index} index={index} pax={pax} passengers={passengers} paxList={paxList}
            isAuthenticated={isAuthenticated} errors={errors} handlePaxChange={handlePaxChange}
            saveToDirectory={saveToDirectory[index] || false}
            onSaveToDirectoryChange={checked => setSaveToDirectory(prev => prev.map((value, i) => i === index ? checked : value))}
            onOpenDirectory={() => { setSelectingForPaxIndex(index); setSelectedTargetType(pax.type); setDirModalOpen(true) }}
          />)}

          <PassengerAddOns paxList={paxList} passengers={passengers} activeAddOnPax={activeAddOnPax} setActiveAddOnPax={setActiveAddOnPax} passengerAddOns={passengerAddOns} handleApplyAddOnsToAll={handleApplyAddOnsToAll} handleUpdateAddOn={handleUpdateAddOn} />
        </div>

        {/* Sidebar */}
        <aside className="flex flex-col gap-6">
          <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sticky top-[140px]">
            <h2 className="text-xl font-bold text-[#102a56] mb-6">Booking breakdown</h2>
            
            <div className="flex flex-col gap-4 text-sm mb-6 border-b border-slate-100 pb-6">
              <div className="flex justify-between">
                <span className="text-slate-500">Base fare ({paxList.length} pax)</span>
                <span className="font-bold">{formatVND(totalBaseFare)}</span>
              </div>
              {returnFlight && (
                <div className="pl-3 border-l-2 border-slate-200 text-xs text-slate-400 space-y-1">
                  <div className="flex justify-between">
                    <span>Outbound ({flight.code}):</span>
                    <span>{flight.price}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Return ({returnFlight.code}):</span>
                    <span>{returnFlight.price}</span>
                  </div>
                </div>
              )}
              <div className="flex justify-between">
                <span className="text-slate-500">Taxes & Fees (10%)</span>
                <span className="font-bold">{formatVND(totalTaxes)}</span>
              </div>
              {addOnsTotal > 0 && (
                <div className="flex justify-between">
                  <span className="text-slate-500">Add-ons (Baggage, Meal...)</span>
                  <span className="font-bold">{formatVND(addOnsTotal)}</span>
                </div>
              )}
            </div>

            <div className="flex flex-col mb-8 pt-2">
              <div className="flex items-end justify-between">
                <span className="font-bold text-slate-800">Estimated total</span>
                <strong className="text-2xl sm:text-3xl font-bold text-[#f59e0b] tabular-nums">{formatVND(finalTotal)}</strong>
              </div>
              <span className="text-[11px] font-medium text-slate-400 mt-1 italic text-right">
                * Seat fees will be calculated at next step
              </span>
            </div>

            {showErrorBanner && Object.keys(errors).length > 0 && (
              <div className="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-2xl text-xs font-semibold flex items-start gap-2.5 animate-in fade-in slide-in-from-top-2">
                <AlertCircle className="size-4 shrink-0 text-red-500 mt-0.5" />
                <div>
                  <p className="font-bold text-red-800">Please complete all required fields (*)</p>
                  <p className="mt-0.5 text-red-600">Fill in the highlighted red fields above to continue.</p>
                </div>
              </div>
            )}

            <Button 
              onClick={handleContinue} 
              className="w-full rounded-xl bg-[#f59e0b] py-6 text-base font-bold text-[#102a56] shadow-md shadow-amber-500/20 hover:bg-[#d97706] cursor-pointer"
            >
              Continue to Seats
              <ArrowRight className="size-5 ml-2" />
            </Button>
          </div>
        </aside>

      </div>

      <PassengerDirectoryModal
        isOpen={dirModalOpen}
        onClose={() => {
          setDirModalOpen(false)
          setSelectingForPaxIndex(null)
          setSelectedTargetType(undefined)
          // Refetch saved passengers in case user added or edited any
          void refreshDirectory()
        }}
        referenceDate={departureDate(flight.departureTime)}
        targetType={selectedTargetType}
        onSelectPassenger={selectingForPaxIndex !== null ? (selected) => {
          applyPassengerFromDirectory(selectingForPaxIndex, selected)
        } : undefined}
      />
    </main>
  )
}
