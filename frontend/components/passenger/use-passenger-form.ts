'use client'

import { useState, useMemo, useEffect, useCallback, useRef } from 'react'
import { useAuth } from '@/lib/auth-context'
import { api } from '@/lib/api'
import { departureDate, passengerTypeAtDate, validatePassengerDraft } from '@/lib/passenger-validation'
import { guardianIndexInput, passengerType, type SearchParams, type PassengerDraft, type PassengerData, type PassengerForm, type ContactForm, type PassengerSlot } from '@/lib/checkout-model'
import { buildPassengerRequests } from '@/lib/checkout-requests'
import { priceBooking, type PassengerAddOns } from '@/lib/pricing'
import type { AdaptedFlight } from '@/lib/flight'
import type { PassengerResponse } from '@/types/api'

export interface PassengerFormOptions {
  flight: AdaptedFlight
  returnFlight?: AdaptedFlight | null
  searchParams: SearchParams
  passengerData?: PassengerDraft | null
  onDraftChange?: (draft: PassengerDraft) => void
  onContinue: (data: PassengerData) => void
}

export function usePassengerForm({ flight, returnFlight, searchParams, passengerData, onDraftChange, onContinue }: PassengerFormOptions) {
  const { user, isAuthenticated } = useAuth()

  // Forms state
  const [contactInfo, setContactInfo] = useState({
    name: passengerData?.contactInfo?.name || user?.fullName || '',
    email: passengerData?.contactInfo?.email || user?.email || '',
    phone: passengerData?.contactInfo?.phone || user?.phone || ''
  })

  // Autofill when user logs in during the flow
  useEffect(() => {
    if (user) setContactInfo(prev => !prev.name || !prev.email ? {
      name: prev.name || user.fullName || '', email: prev.email || user.email || '', phone: prev.phone || user.phone || '',
    } : prev)
  }, [user])

  // Create passenger forms based on searchParams
  const totalAdults = searchParams.passengers.adults
  const totalChildren = searchParams.passengers.children
  const totalInfants = searchParams.passengers.infants

  const paxList = useMemo(() => {
    const list: PassengerSlot[] = []
    for (let i = 0; i < totalAdults; i++) list.push({ type: 'Adult', title: `Passenger ${list.length + 1}` })
    for (let i = 0; i < totalChildren; i++) list.push({ type: 'Child', title: `Passenger ${list.length + 1}` })
    for (let i = 0; i < totalInfants; i++) list.push({ type: 'Infant', title: `Passenger ${list.length + 1}` })
    return list
  }, [totalAdults, totalChildren, totalInfants])

  const [passengers, setPassengers] = useState<PassengerForm[]>(
    passengerData?.passengers.map(p => ({ ...p, guardianIndex: guardianIndexInput(p.guardianIndex) })) || paxList.map((p, idx) => ({
      title: p.type === 'Adult' ? 'Mr' : 'Mstr',
      name: '',
      dob: '',
      idType: p.type === 'Adult' ? 'CITIZEN_ID' : '',
      id: '',
      idExpiry: '',
      nationality: 'Vietnam',
      guardianIndex: p.type === 'Infant' ? Math.min(Math.max(0, idx - totalAdults - totalChildren), Math.max(0, totalAdults - 1)) : null
    }))
  )

  const [errors, setErrors] = useState<Record<string, string>>({})
  const [showErrorBanner, setShowErrorBanner] = useState(false)
  const [copyToPax1, setCopyToPax1] = useState(false)
  // Passenger directory state
  const [savedPassengers, setSavedPassengers] = useState<PassengerResponse[]>([])
  const [saveToDirectory, setSaveToDirectory] = useState<boolean[]>(new Array(paxList.length).fill(false))

  const mounted = useRef(false)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const refreshDirectory = useCallback(async () => {
    if (!isAuthenticated) return
    try {
      const data = await api.getMyPassengers()
      if (mounted.current) setSavedPassengers(data || [])
    } catch { /* Directory availability must not block checkout. */ }
  }, [isAuthenticated])

  // Fetch saved passengers when user is authenticated
  useEffect(() => {
    let active = true
    if (isAuthenticated) {
      api.getMyPassengers().then(data => { if (active) setSavedPassengers(data || []) }).catch(() => {})
    } else setSavedPassengers([])
    return () => { active = false }
  }, [isAuthenticated, user?.id])

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

    const title = currentPaxMeta.type === 'Adult'
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
    const type = passengerTypeAtDate(sp.dateOfBirth, departureDate(flight.departureTime))
    const expectedType = type === 'ADULT' ? 'Adult' : type === 'CHILD' ? 'Child' : type === 'INFANT' ? 'Infant' : null

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
  const [passengerAddOns, setPassengerAddOns] = useState<Record<number, PassengerAddOns>>(() => {
    if (passengerData?.passengerAddOns) return passengerData.passengerAddOns;
    const initial: Record<number, PassengerAddOns> = {};
    paxList.forEach((_, idx) => {
      initial[idx] = { baggage: 0, meal: false, insurance: false };
    });
    return initial;
  });
  useEffect(() => { onDraftChange?.({ contactInfo, passengers, passengerAddOns }) }, [contactInfo, passengers, passengerAddOns, onDraftChange])

  const [activeAddOnPax, setActiveAddOnPax] = useState(0);

  const handleUpdateAddOn = <K extends keyof PassengerAddOns,>(paxIndex: number, field: K, value: PassengerAddOns[K]) => {
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
    const next: Record<number, PassengerAddOns> = {};
    paxList.forEach((p, idx) => {
      if (p.type !== 'Infant') {
        next[idx] = { ...currentSettings };
      }
    });
    setPassengerAddOns(next);
  };

  // Handlers
  const handleContactChange = (field: keyof ContactForm, value: string) => {
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

  const handlePaxChange = <K extends keyof PassengerForm,>(index: number, field: K, value: PassengerForm[K]) => {
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

  const handleContinue = () => {
    const errs = validatePassengerDraft({ contactInfo, passengers, passengerAddOns }, paxList, departureDate(flight.departureTime))
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

    const passengerRequests = buildPassengerRequests(paxList, passengers)

    // Auto-save passengers to personal directory if user is authenticated and checked the option
    if (isAuthenticated) {
      paxList.forEach((_, index) => {
        if (saveToDirectory[index]) {
          const req = passengerRequests[index]
          api.createPassenger(req).then(created => {
            if (mounted.current) setSavedPassengers(prev => [created, ...prev])
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
      pricing: {
        totalBaseFare,
        totalTaxes,
        addOnsTotal,
        discount: 0 // Will handle discount in Payment step
      }
    })
  }



  // Estimate until actual seat prices are selected. Add-ons currently apply to outbound only.
  const types = paxList.map(p => passengerType(p.type))
  const basePrices = returnFlight ? [flight.basePrice, returnFlight.basePrice] : [flight.basePrice]
  const estimate = priceBooking(basePrices, types, basePrices.flatMap(price => types.flatMap((type, passengerIndex) => type === 'INFANT' ? [] : [{ passengerIndex, price }])), passengerAddOns)
  const totalBaseFare = estimate.fareAmount
  const addOnsTotal = estimate.addOnsTotal
  const totalTaxes = estimate.taxAmount
  const finalTotal = estimate.totalAmount

  return {
    isAuthenticated, contactInfo, passengers, paxList, errors, showErrorBanner,
    copyToPax1, savedPassengers, uniqueSavedPassengers, saveToDirectory, setSaveToDirectory,
    passengerAddOns, activeAddOnPax, setActiveAddOnPax,
    totalBaseFare, totalTaxes, addOnsTotal, finalTotal,
    handleContactChange, handleCopyToggle, handlePaxChange, handleUpdateAddOn,
    handleApplyAddOnsToAll, handleQuickFillFirstAvailable, applyPassengerFromDirectory,
    handleContinue, refreshDirectory,
  }
}

export type PassengerFormController = ReturnType<typeof usePassengerForm>
