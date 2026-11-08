'use client'

import { formatVND } from '@/lib/format'
import type { PassengerFormController } from './use-passenger-form'
type Props = Pick<PassengerFormController, 'paxList' | 'passengers' | 'activeAddOnPax' | 'setActiveAddOnPax' | 'passengerAddOns' | 'handleApplyAddOnsToAll' | 'handleUpdateAddOn'>
export function PassengerAddOns({ paxList, passengers, activeAddOnPax, setActiveAddOnPax, passengerAddOns, handleApplyAddOnsToAll, handleUpdateAddOn }: Props) {
  const selected = passengerAddOns[activeAddOnPax] || { baggage: 0, meal: false, insurance: false }
  return <section className="rounded-2xl border border-slate-200 bg-white p-6">
    <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
      <div><h2 className="text-lg font-semibold text-[#102a56]">Optional extras</h2><p className="mt-1 text-sm text-slate-500">Per passenger, outbound flight only. You can continue without extras.</p></div>
      {paxList.filter(p => p.type !== 'Infant').length > 1 && <button type="button" onClick={handleApplyAddOnsToAll} className="cursor-pointer text-sm text-blue-700 underline">Apply to all passengers</button>}
    </div>
    {paxList.filter(p => p.type !== 'Infant').length > 1 && <div className="mb-5 flex flex-wrap gap-2">{paxList.map((p,i) => p.type !== 'Infant' && <button type="button" key={i} onClick={() => setActiveAddOnPax(i)} aria-pressed={i===activeAddOnPax} className={`cursor-pointer rounded-lg border px-3 py-2 text-sm ${i===activeAddOnPax?'border-slate-700 bg-slate-100':'border-slate-200'}`}>{passengers[i]?.name || p.title}</button>)}</div>}
    <fieldset>
      <legend className="mb-2 text-sm font-semibold text-slate-800">Baggage · {passengers[activeAddOnPax]?.name || `Passenger ${activeAddOnPax+1}`}</legend>
      <div className="divide-y divide-slate-100">{[{price:0,label:'Carry-on only (7kg)'},{price:200000,label:'+20kg Checked Bag'},{price:400000,label:'+40kg Checked Bag'}].map(option => <label key={option.price} className="flex cursor-pointer items-center gap-3 py-3 text-sm">
        <input type="radio" name={`baggage-${activeAddOnPax}`} checked={selected.baggage===option.price} onChange={() => handleUpdateAddOn(activeAddOnPax,'baggage',option.price)} className="size-4 accent-[#102a56]" />
        <span className="flex-1 text-slate-700">{option.label}</span><span className="font-medium text-slate-700">{option.price ? `+${formatVND(option.price)}`:'Included'}</span>
      </label>)}</div>
    </fieldset>
    <div className="mt-3 divide-y divide-slate-100 border-t border-slate-200">{([{key:'meal',label:'In-Flight Hot Meal',price:80000},{key:'insurance',label:'Flight Protection Insurance',price:50000}] as const).map(option => <label key={option.key} className="flex cursor-pointer items-center gap-3 py-4 text-sm">
      <input type="checkbox" checked={selected[option.key]} onChange={e => handleUpdateAddOn(activeAddOnPax,option.key,e.target.checked)} className="size-4 accent-[#102a56]" />
      <span className="flex-1 text-slate-700">{option.label}</span><span className="font-medium text-slate-700">+{formatVND(option.price)}</span>
    </label>)}</div>
  </section>
}
