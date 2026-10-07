'use client'

import { useState, useMemo, useEffect } from 'react'
import { 
  Calendar, 
  Check, 
  Plane, 
  X, 
  ShieldCheck, 
  Utensils, 
  HelpCircle, 
  ArrowRight, 
  ArrowLeft, 
  AlertCircle,
  Users,
  Sparkles,
  Plus
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatVND } from '@/lib/format'
import { useAuth } from '@/lib/auth-context'
import { UserNav } from '@/components/auth/UserNav'
import { api } from '@/lib/api'
import type { PassengerResponse } from '@/types/api'
import { PassengerDirectoryModal } from '@/components/passenger/PassengerDirectoryModal'
import type { SearchParams } from '@/components/home-page'

// Define the flight type as it comes from page.tsx (adapted)
type AdaptedFlight = {
  id: number;
  airline: string;
  code: string;
  logo: string;
  tone: string;
  depart: string;
  arrive: string;
  duration: string;
  price: string;
  aircraft: string;
  badge: string;
}

export function PassengerDetails({ 
  flight, 
  returnFlight,
  searchParams,
  passengerData,
  onBack, 
  onHome, 
  onContinue 
}: { 
  flight: AdaptedFlight | null; 
  returnFlight?: AdaptedFlight | null;
  searchParams: SearchParams;
  passengerData?: any;
  onBack: () => void; 
  onHome: () => void; 
  onContinue: (data: any) => void;
}) {
  const { user, isAuthenticated } = useAuth()
  
  if (!flight || !searchParams) {
    return (
      <div className="min-h-screen bg-[#f7f8fa] flex flex-col items-center justify-center p-5">
        <div className="bg-white p-8 rounded-3xl shadow-sm border border-slate-200 text-center max-w-md">
          <Plane className="size-12 text-slate-300 mx-auto mb-4" />
          <h2 className="text-xl font-bold text-slate-800 mb-2">Session Expired</h2>
          <p className="text-slate-500 text-sm mb-6">Your booking session has expired or is invalid. Please start a new search.</p>
          <Button onClick={onHome} className="w-full bg-[#102a56] hover:bg-[#1a3d75] rounded-xl h-12 font-bold">
            Start New Search
          </Button>
        </div>
      </div>
    )
  }

  // Parse original base price
  const basePricePerPax = Number(flight.price.replace(/\D/g, ''))
  const returnBasePricePerPax = returnFlight ? Number(returnFlight.price.replace(/\D/g, '')) : 0
  const combinedBasePricePerPax = basePricePerPax + returnBasePricePerPax

  // Forms state
  const [contactInfo, setContactInfo] = useState({ 
    name: passengerData?.contactInfo?.name || user?.fullName || '', 
    email: passengerData?.contactInfo?.email || user?.email || '', 
    phone: passengerData?.contactInfo?.phone || user?.phone || '' 
  })

  // Autofill when user logs in during the flow
  useEffect(() => {
    if (user && (!contactInfo.name || !contactInfo.email)) {
      setContactInfo(prev => ({
        name: prev.name || user.fullName || '',
        email: prev.email || user.email || '',
        phone: prev.phone || user.phone || ''
      }))
    }
  }, [user])
  
  // Create passenger forms based on searchParams
  const totalAdults = searchParams.passengers.adults
  const totalChildren = searchParams.passengers.children
  const totalInfants = searchParams.passengers.infants
  
  const paxList = useMemo(() => {
    const list: { type: 'Adult' | 'Child' | 'Infant', title: string }[] = []
    for (let i = 0; i < totalAdults; i++) list.push({ type: 'Adult', title: `Passenger ${list.length + 1}` })
    for (let i = 0; i < totalChildren; i++) list.push({ type: 'Child', title: `Passenger ${list.length + 1}` })
    for (let i = 0; i < totalInfants; i++) list.push({ type: 'Infant', title: `Passenger ${list.length + 1}` })
    return list
  }, [totalAdults, totalChildren, totalInfants])

  const [passengers, setPassengers] = useState(
    passengerData?.passengers || paxList.map((p, idx) => ({ 
      title: p.type === 'Adult' ? 'Mr' : 'Mstr', 
      name: '', 
      dob: '', 
      idType: p.type === 'Adult' ? 'CITIZEN_ID' : '', 
      id: '',
      idExpiry: '',
      nationality: 'Vietnam',
      guardianIndex: p.type === 'Infant' ? Math.min(Math.max(0, idx - totalAdults - totalChildren), Math.max(0, totalAdults - 1)) : undefined
    }))
  )

  const [errors, setErrors] = useState<Record<string, string>>({})
  const [showErrorBanner, setShowErrorBanner] = useState(false)
  const [copyToPax1, setCopyToPax1] = useState(false)
  // Passenger directory state
  const [savedPassengers, setSavedPassengers] = useState<PassengerResponse[]>([])
  const [dirModalOpen, setDirModalOpen] = useState(false)
  const [selectingForPaxIndex, setSelectingForPaxIndex] = useState<number | null>(null)
  const [selectedTargetType, setSelectedTargetType] = useState<'Adult' | 'Child' | 'Infant' | undefined>(undefined)
  const [saveToDirectory, setSaveToDirectory] = useState<boolean[]>(new Array(paxList.length).fill(false))

  // Fetch saved passengers when user is authenticated
  useEffect(() => {
    if (isAuthenticated) {
      api.getMyPassengers()
        .then(data => setSavedPassengers(data || []))
        .catch(err => console.warn('Could not load directory:', err))
    } else {
      setSavedPassengers([])
    }
  }, [isAuthenticated])

  // Unique saved passengers for quick suggestion pill bar (deduplicated by name + dob, limit 6)
  const uniqueSavedPassengers = useMemo(() => {
    const seen = new Set<string>()
    return savedPassengers.filter((p) => {
      const key = `${p.fullName?.trim().toUpperCase()}_${p.dateOfBirth}`
      if (seen.has(key)) return false
      seen.add(key)
      return true
    }).slice(0, 6)
  }, [savedPassengers])

  const applyPassengerFromDirectory = (paxIndex: number, p: PassengerResponse) => {
    const newPassengers = [...passengers]
    const currentPaxMeta = paxList[paxIndex]
    
    let title = currentPaxMeta.type === 'Adult' 
      ? (p.gender === 'FEMALE' ? 'Ms' : 'Mr')
      : (p.gender === 'FEMALE' ? 'Miss' : 'Mstr')

    newPassengers[paxIndex] = {
      ...newPassengers[paxIndex],
      title,
      name: p.fullName,
      dob: p.dateOfBirth,
      idType: p.identityType || (currentPaxMeta.type === 'Adult' ? 'CITIZEN_ID' : ''),
      id: p.identityNumber || '',
      idExpiry: p.identityExpiry || '',
      nationality: p.nationality || 'Vietnam'
    }
    setPassengers(newPassengers)

    // Clear validation errors for this passenger
    setErrors(prev => {
      const next = { ...prev }
      delete next[`pax_${paxIndex}_name`]
      delete next[`pax_${paxIndex}_dob`]
      delete next[`pax_${paxIndex}_id`]
      delete next[`pax_${paxIndex}_idExpiry`]
      return next
    })
  }

  const handleQuickFillFirstAvailable = (sp: PassengerResponse) => {
    const birth = new Date(sp.dateOfBirth)
    const now = new Date()
    let age = now.getFullYear() - birth.getFullYear()
    const m = now.getMonth() - birth.getMonth()
    if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) age--
    const expectedType: 'Adult' | 'Child' | 'Infant' = age < 2 ? 'Infant' : age < 12 ? 'Child' : 'Adult'

    let targetIndex = paxList.findIndex((p, idx) => p.type === expectedType && !passengers[idx].name.trim())
    if (targetIndex === -1) {
      targetIndex = paxList.findIndex(p => p.type === expectedType)
    }
    if (targetIndex !== -1) {
      applyPassengerFromDirectory(targetIndex, sp)
      const el = document.getElementById(`pax_${targetIndex}_name`)
      el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    } else {
      alert(`No available passenger slot matching age category ${expectedType} on this flight.`)
    }
  }

  // Per-passenger Add-ons state
  const [passengerAddOns, setPassengerAddOns] = useState<Record<number, { baggage: number; meal: boolean; insurance: boolean }>>(() => {
    if (passengerData?.passengerAddOns) return passengerData.passengerAddOns;
    const initial: Record<number, { baggage: number; meal: boolean; insurance: boolean }> = {};
    paxList.forEach((_, idx) => {
      initial[idx] = { baggage: 0, meal: false, insurance: false };
    });
    return initial;
  });
  const [activeAddOnPax, setActiveAddOnPax] = useState(0);

  const handleUpdateAddOn = (paxIndex: number, field: 'baggage' | 'meal' | 'insurance', value: any) => {
    setPassengerAddOns(prev => ({
      ...prev,
      [paxIndex]: {
        ...(prev[paxIndex] || { baggage: 0, meal: false, insurance: false }),
        [field]: value
      }
    }));
  };

  const handleApplyAddOnsToAll = () => {
    const currentSettings = passengerAddOns[activeAddOnPax] || { baggage: 0, meal: false, insurance: false };
    const next: Record<number, { baggage: number; meal: boolean; insurance: boolean }> = {};
    paxList.forEach((p, idx) => {
      if (p.type !== 'Infant') {
        next[idx] = { ...currentSettings };
      }
    });
    setPassengerAddOns(next);
  };

  // Handlers
  const handleContactChange = (field: string, value: string) => {
    const newContact = { ...contactInfo, [field]: value }
    setContactInfo(newContact)
    if (errors[`contact_${field}`]) {
      setErrors(prev => {
        const next = { ...prev }
        delete next[`contact_${field}`]
        return next
      })
    }
    if (copyToPax1 && field === 'name') {
      const newPax = [...passengers]
      newPax[0].name = value
      setPassengers(newPax)
      if (errors['pax_0_name']) {
        setErrors(prev => {
          const next = { ...prev }
          delete next['pax_0_name']
          return next
        })
      }
    }
  }

  const handleCopyToggle = (checked: boolean) => {
    setCopyToPax1(checked)
    if (checked) {
      const newPax = [...passengers]
      newPax[0].name = contactInfo.name
      setPassengers(newPax)
      if (contactInfo.name && errors['pax_0_name']) {
        setErrors(prev => {
          const next = { ...prev }
          delete next['pax_0_name']
          return next
        })
      }
    }
  }

  const handlePaxChange = (index: number, field: string, value: string) => {
    const newPax = [...passengers]
    newPax[index] = { ...newPax[index], [field]: value }
    setPassengers(newPax)
    if (errors[`pax_${index}_${field}`]) {
      setErrors(prev => {
        const next = { ...prev }
        delete next[`pax_${index}_${field}`]
        return next
      })
    }
  }

  const validateForm = () => {
    const errs: Record<string, string> = {}

    // 1. Contact validation
    if (!contactInfo.name.trim()) {
      errs['contact_name'] = 'Contact full name is required'
    }
    if (!contactInfo.email.trim()) {
      errs['contact_email'] = 'Email address is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contactInfo.email.trim())) {
      errs['contact_email'] = 'Please enter a valid email address'
    }
    if (!contactInfo.phone.trim()) {
      errs['contact_phone'] = 'Phone number is required'
    } else if (contactInfo.phone.replace(/\D/g, '').length < 8) {
      errs['contact_phone'] = 'Phone number must be at least 8 digits'
    }

    // 2. Passengers validation
    passengers.forEach((p: any, idx: number) => {
      const paxMeta = paxList[idx]
      if (!p.name.trim()) {
        errs[`pax_${idx}_name`] = `Passenger ${idx + 1} full name is required`
      }
      if (!p.dob) {
        errs[`pax_${idx}_dob`] = `Date of birth is required`
      } else {
        const dobDate = new Date(p.dob)
        const today = new Date()
        let age = today.getFullYear() - dobDate.getFullYear()
        const m = today.getMonth() - dobDate.getMonth()
        if (m < 0 || (m === 0 && today.getDate() < dobDate.getDate())) {
          age--
        }
        if (paxMeta.type === 'Adult' && age < 12) {
          errs[`pax_${idx}_dob`] = `Adult must be at least 12 years old`
        } else if (paxMeta.type === 'Child' && (age < 2 || age >= 12)) {
          errs[`pax_${idx}_dob`] = `Child must be between 2 and 11 years old`
        } else if (paxMeta.type === 'Infant' && age >= 2) {
          errs[`pax_${idx}_dob`] = `Infant must be under 2 years old`
        }
      }

      const type = p.idType || 'CITIZEN_ID';
      if (p.id && p.id.trim() && type === 'CITIZEN_ID' && !/^\d+$/.test(p.id.trim())) {
        errs[`pax_${idx}_id`] = `Citizen ID must contain only numbers`
      }

      if (paxMeta.type === 'Adult') {
        if (!p.id || !p.id.trim()) {
          errs[`pax_${idx}_id`] = `National ID or Passport number is required for Adult`
        }
      }

      if (p.idType === 'PASSPORT') {
        if (!p.idExpiry) {
          errs[`pax_${idx}_idExpiry`] = `Passport expiry date is required`
        } else if (new Date(p.idExpiry) <= new Date()) {
          errs[`pax_${idx}_idExpiry`] = `Passport has expired. Must be valid for travel`
        }
      }
    })

    // Validate Infant Guardian: Each adult can carry at most 1 lap infant
    const infantGuardians = new Set<number>()
    passengers.forEach((p: any, idx: number) => {
      if (paxList[idx].type === 'Infant') {
        const gIdx = p.guardianIndex !== undefined ? Number(p.guardianIndex) : 0
        if (gIdx < 0 || gIdx >= totalAdults) {
          errs[`pax_${idx}_guardian`] = 'Please select an accompanying adult guardian'
        } else if (infantGuardians.has(gIdx)) {
          errs[`pax_${idx}_guardian`] = 'Each adult can only accompany at most one infant on lap'
        } else {
          infantGuardians.add(gIdx)
        }
      }
    })

    return errs
  }

  const handleContinue = () => {
    const errs = validateForm()
    if (Object.keys(errs).length > 0) {
      setErrors(errs)
      setShowErrorBanner(true)

      const firstKey = Object.keys(errs)[0]
      const el = document.getElementById(firstKey)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        el.focus?.()
      }
      return
    }

    setShowErrorBanner(false)
    setErrors({})

    const passengerRequests = paxList.map((pax, index) => {
      const p = passengers[index];
      let gender = 'MALE';
      if (p.title === 'Ms' || p.title === 'Miss') gender = 'FEMALE';
      
      return {
        fullName: p.name.trim(),
        dateOfBirth: p.dob,
        gender,
        passengerType: pax.type.toUpperCase(),
        identityType: p.idType || (pax.type === 'Adult' ? 'CITIZEN_ID' : undefined),
        identityNumber: p.id ? p.id.trim() : undefined,
        identityExpiry: p.idType === 'PASSPORT' && p.idExpiry ? p.idExpiry : undefined,
        nationality: p.nationality || 'Vietnam',
        guardianPassengerIndex: pax.type === 'Infant' ? (p.guardianIndex !== undefined ? Number(p.guardianIndex) : 0) : undefined
      };
    });

    // Auto-save passengers to personal directory if user is authenticated and checked the option
    if (isAuthenticated) {
      paxList.forEach((pax, index) => {
        if (saveToDirectory[index]) {
          const req = passengerRequests[index]
          api.createPassenger({
            fullName: req.fullName,
            dateOfBirth: req.dateOfBirth,
            gender: req.gender as any,
            nationality: req.nationality,
            passengerType: req.passengerType as any,
            identityType: req.identityType as any,
            identityNumber: req.identityNumber,
            identityExpiry: req.identityExpiry
          }).then(created => {
            setSavedPassengers(prev => [created, ...prev])
          }).catch(e => console.warn('Auto-save to directory failed:', e))
        }
      })
    }

    onContinue({
      paxList,
      passengers,
      passengerRequests,
      passengerAddOns,
      contactInfo,
      addOns: {
        total: addOnsTotal,
        passengerAddOns
      },
      pricing: {
        totalBaseFare,
        totalTaxes,
        addOnsTotal,
        discount: 0 // Will handle discount in Payment step
      }
    })
  }

  const handleClose = () => {
    if (contactInfo.name || passengers[0].name) {
      if (window.confirm("Are you sure you want to leave? Your entered details will be lost.")) {
        onBack()
      }
    } else {
      onBack()
    }
  }

  // Price calculations
  const totalBaseFare = combinedBasePricePerPax * (totalAdults + totalChildren + (totalInfants * 0.1)) // Infants pay 10%
  const totalTaxes = totalBaseFare * 0.1
  const addOnsTotal = Object.entries(passengerAddOns).reduce((sum, [idx, addOn]) => {
    if (paxList[Number(idx)]?.type === 'Infant') return sum;
    let s = 0;
    if (addOn.baggage) s += addOn.baggage;
    if (addOn.meal) s += 80000;
    if (addOn.insurance) s += 50000;
    return sum + s;
  }, 0)
  const finalTotal = totalBaseFare + totalTaxes + addOnsTotal

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
          
          {/* Contact Info */}
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

          {/* Passenger Forms */}
          {paxList.map((pax, index) => {
            return (
              <section key={index} className="rounded-3xl border border-slate-200 bg-white shadow-sm overflow-hidden">
                <div className="bg-slate-50 px-6 py-4 border-b border-slate-100 flex items-center justify-between flex-wrap gap-2">
                  <div className="flex items-center gap-2">
                    <h3 className="font-bold text-[#102a56] text-lg">
                      {pax.title} <span className="font-normal text-slate-400 mx-2">|</span> {pax.type}
                    </h3>
                    {pax.type === 'Infant' && (
                      <span className="bg-slate-200 text-slate-600 px-3 py-1 rounded-full text-xs font-bold">
                        On Adult's Lap
                      </span>
                    )}
                  </div>

                  {isAuthenticated && (
                    <button
                      type="button"
                      onClick={() => {
                        setSelectingForPaxIndex(index)
                        setSelectedTargetType(pax.type)
                        setDirModalOpen(true)
                      }}
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
                          onChange={e => handlePaxChange(index, 'idType', e.target.value)}
                          className="w-[120px] h-[46px] rounded-xl border border-slate-200 px-3 text-sm focus:border-[#2563eb] outline-none bg-white cursor-pointer"
                        >
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
                          Passport Expiry Date <span className="text-red-500 font-bold ml-0.5">*</span>
                        </label>
                        <div className="relative">
                          <input 
                            id={`pax_${index}_idExpiry`}
                            type="date"
                            min={new Date().toISOString().split('T')[0]}
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
                        onChange={e => handlePaxChange(index, 'guardianIndex', e.target.value)}
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
                          checked={saveToDirectory[index] || false}
                          onChange={e => {
                            const next = [...saveToDirectory]
                            next[index] = e.target.checked
                            setSaveToDirectory(next)
                          }}
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
          })}

          {/* Add-ons */}
          <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
              <div>
                <h2 className="text-xl font-bold text-[#102a56] mb-1">Add-on services</h2>
                <p className="text-sm text-slate-500">Customize extra baggage, meals, and protection per passenger.</p>
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
                <span className="font-bold text-slate-800">Subtotal</span>
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
          if (isAuthenticated) {
            api.getMyPassengers().then(data => setSavedPassengers(data || [])).catch(() => {})
          }
        }}
        targetType={selectedTargetType}
        onSelectPassenger={selectingForPaxIndex !== null ? (selected) => {
          applyPassengerFromDirectory(selectingForPaxIndex, selected)
        } : undefined}
      />
    </main>
  )
}
