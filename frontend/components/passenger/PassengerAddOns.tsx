'use client'

import { Check, ShieldCheck, Utensils } from 'lucide-react'
import type { PassengerFormController } from './use-passenger-form'
type Props = Pick<PassengerFormController, 'paxList' | 'passengers' | 'activeAddOnPax' | 'setActiveAddOnPax' | 'passengerAddOns' | 'handleApplyAddOnsToAll' | 'handleUpdateAddOn'>
export function PassengerAddOns({ paxList, passengers, activeAddOnPax, setActiveAddOnPax, passengerAddOns, handleApplyAddOnsToAll, handleUpdateAddOn }: Props) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h2 className="text-xl font-bold text-[#102a56] mb-1">Add-on services</h2>
          <p className="text-sm text-slate-500">Customize extras per passenger for the outbound flight. Return-flight extras are not included.</p>
        </div>
        {paxList.filter(p => p.type !== 'Infant').length > 1 && (
          <button
            type="button"
            onClick={handleApplyAddOnsToAll}
            className="text-xs font-bold text-[#102a56] bg-slate-100 hover:bg-slate-200 px-3.5 py-2 rounded-xl transition-colors cursor-pointer w-fit"
          >
            Apply package to all passengers
          </button>
        )}
      </div>

      {/* Passenger Selector Tabs */}
      {paxList.filter(p => p.type !== 'Infant').length > 1 && (
        <div className="flex flex-wrap gap-2 mb-6 p-1.5 bg-slate-50 rounded-2xl border border-slate-100">
          {paxList.map((p, idx) => {
            if (p.type === 'Infant') return null;
            const isSelected = activeAddOnPax === idx;
            const pName = passengers[idx]?.name ? passengers[idx].name : p.title;
            const paxAddOn = passengerAddOns[idx] || { baggage: 0, meal: false, insurance: false };
            const hasExtras = paxAddOn.baggage > 0 || paxAddOn.meal || paxAddOn.insurance;

            return (
              <button
                key={idx}
                type="button"
                onClick={() => setActiveAddOnPax(idx)}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                  isSelected
                    ? 'bg-[#102a56] text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-white'
                }`}
              >
                <span className="truncate max-w-[130px]">{pName}</span>
                {hasExtras && (
                  <span className={`size-1.5 rounded-full ${isSelected ? 'bg-amber-400' : 'bg-emerald-500'}`} />
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* Baggage for active passenger */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
            Baggage Allowance ({passengers[activeAddOnPax]?.name || `Passenger ${activeAddOnPax + 1}`})
          </h3>
          <span className="text-xs font-medium text-slate-400">Standard 7kg carry-on included</span>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          {[
            { weight: 0, price: 0, label: 'Carry-on only (7kg)', desc: 'Included free' },
            { weight: 20, price: 200000, label: '+20kg Checked Bag', desc: '+200.000 ₫' },
            { weight: 40, price: 400000, label: '+40kg Checked Bag', desc: '+400.000 ₫' },
          ].map(opt => {
            const currentBag = passengerAddOns[activeAddOnPax]?.baggage || 0;
            const isSelected = currentBag === opt.price;
            return (
              <button
                key={opt.price}
                type="button"
                onClick={() => handleUpdateAddOn(activeAddOnPax, 'baggage', opt.price)}
                className={`relative flex flex-col items-center justify-center rounded-2xl border-2 p-3.5 cursor-pointer text-center transition-all ${
                  isSelected ? 'border-[#102a56] bg-slate-50' : 'border-slate-200 hover:border-slate-300 bg-white'
                }`}
              >
                <span className="text-sm font-bold text-slate-800 mb-0.5">{opt.label}</span>
                <span className={`text-xs font-semibold ${opt.price === 0 ? 'text-emerald-600' : 'text-[#f59e0b]'}`}>
                  {opt.desc}
                </span>
                {isSelected && (
                  <div className="absolute top-2 right-2 text-[#102a56] bg-white rounded-full p-0.5 shadow-2xs">
                    <Check className="size-3 stroke-[3]" />
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Meal & Insurance */}
      <div className="grid gap-4 md:grid-cols-2">
        <button
          type="button"
          onClick={() => handleUpdateAddOn(activeAddOnPax, 'meal', !(passengerAddOns[activeAddOnPax]?.meal))}
          className={`flex text-left gap-3.5 rounded-2xl border-2 p-4 cursor-pointer transition-all ${
            passengerAddOns[activeAddOnPax]?.meal ? 'border-[#f59e0b] bg-amber-50/40' : 'border-slate-200 hover:border-slate-300 bg-white'
          }`}
        >
          <div className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${
            passengerAddOns[activeAddOnPax]?.meal ? 'bg-[#f59e0b] text-white' : 'bg-slate-100 text-slate-500'
          }`}>
            <Utensils className="size-5" />
          </div>
          <div className="flex-1">
            <p className="text-sm font-bold text-slate-900">In-Flight Hot Meal</p>
            <p className="text-xs text-slate-500 mt-0.5 mb-2">Premium meal combo with water.</p>
            <p className="text-xs font-bold text-[#f59e0b]">+80.000 ₫ / pax</p>
          </div>
          {passengerAddOns[activeAddOnPax]?.meal && (
            <Check className="size-4 text-[#f59e0b] stroke-[3] shrink-0 mt-1" />
          )}
        </button>

        <button
          type="button"
          onClick={() => handleUpdateAddOn(activeAddOnPax, 'insurance', !(passengerAddOns[activeAddOnPax]?.insurance))}
          className={`flex text-left gap-3.5 rounded-2xl border-2 p-4 cursor-pointer transition-all ${
            passengerAddOns[activeAddOnPax]?.insurance ? 'border-emerald-500 bg-emerald-50/50' : 'border-slate-200 hover:border-slate-300 bg-white'
          }`}
        >
          <div className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${
            passengerAddOns[activeAddOnPax]?.insurance ? 'bg-emerald-500 text-white' : 'bg-slate-100 text-slate-500'
          }`}>
            <ShieldCheck className="size-5" />
          </div>
          <div className="flex-1">
            <p className="text-sm font-bold text-slate-900">Flight Protection Insurance</p>
            <p className="text-xs text-slate-500 mt-0.5 mb-2">Delay & cancellation compensation.</p>
            <p className="text-xs font-bold text-emerald-600">+50.000 ₫ / pax</p>
          </div>
          {passengerAddOns[activeAddOnPax]?.insurance && (
            <Check className="size-4 text-emerald-500 stroke-[3] shrink-0 mt-1" />
          )}
        </button>
      </div>
    </section>
  )
}
