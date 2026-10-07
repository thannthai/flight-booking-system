'use client'

import React, { useState, useEffect } from 'react'
import { 
  X, 
  Users, 
  Plus, 
  Trash2, 
  Edit2, 
  Check, 
  Calendar, 
  CreditCard, 
  Globe, 
  User, 
  AlertCircle, 
  Loader2, 
  Sparkles,
  ArrowLeft
} from 'lucide-react'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth-context'
import type { PassengerResponse, PassengerRequest, DocumentType, Gender, PassengerType } from '@/types/api'

interface PassengerDirectoryModalProps {
  isOpen: boolean
  onClose: () => void
  onSelectPassenger?: (passenger: PassengerResponse) => void
  targetType?: 'Adult' | 'Child' | 'Infant'
}

export function PassengerDirectoryModal({
  isOpen,
  onClose,
  onSelectPassenger,
  targetType,
}: PassengerDirectoryModalProps) {
  const { user, isAuthenticated } = useAuth()
  const [passengers, setPassengers] = useState<PassengerResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  
  // View mode: 'list' | 'add' | 'edit'
  const [viewMode, setViewMode] = useState<'list' | 'add' | 'edit'>('list')
  const [editingId, setEditingId] = useState<number | null>(null)
  
  // Form fields
  const [fullName, setFullName] = useState('')
  const [dob, setDob] = useState('')
  const [gender, setGender] = useState<Gender>('MALE')
  const [nationality, setNationality] = useState('Vietnam')
  const [paxType, setPaxType] = useState<PassengerType>('ADULT')
  const [idType, setIdType] = useState<DocumentType>('CITIZEN_ID')
  const [idNumber, setIdNumber] = useState('')
  const [idExpiry, setIdExpiry] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  // Fetch saved passengers
  const fetchPassengers = async () => {
    if (!isAuthenticated) return
    setLoading(true)
    setError(null)
    try {
      const data = await api.getMyPassengers()
      setPassengers(data || [])
    } catch (err: any) {
      console.error('Failed to load passenger directory:', err)
      setError(err.message || 'Failed to load traveler directory')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (isOpen && isAuthenticated) {
      fetchPassengers()
      setViewMode('list')
      setFormError(null)
    }
  }, [isOpen, isAuthenticated])

  if (!isOpen) return null

  // Calculate passenger category (Adult/Child/Infant) based on DOB
  const calculatePaxType = (dateOfBirth: string): 'Adult' | 'Child' | 'Infant' => {
    if (!dateOfBirth) return 'Adult'
    const birth = new Date(dateOfBirth)
    const now = new Date()
    let age = now.getFullYear() - birth.getFullYear()
    const m = now.getMonth() - birth.getMonth()
    if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) {
      age--
    }
    if (age < 2) return 'Infant'
    if (age < 12) return 'Child'
    return 'Adult'
  }

  const handleOpenAdd = () => {
    setFullName('')
    setDob('')
    setGender('MALE')
    setNationality('Vietnam')
    setPaxType(targetType === 'Child' ? 'CHILD' : targetType === 'Infant' ? 'INFANT' : 'ADULT')
    setIdType('CITIZEN_ID')
    setIdNumber('')
    setIdExpiry('')
    setEditingId(null)
    setFormError(null)
    setViewMode('add')
  }

  const handleOpenEdit = (p: PassengerResponse) => {
    setFullName(p.fullName)
    setDob(p.dateOfBirth)
    setGender(p.gender)
    setNationality(p.nationality || 'Vietnam')
    const calculated = calculatePaxType(p.dateOfBirth)
    setPaxType(calculated === 'Child' ? 'CHILD' : calculated === 'Infant' ? 'INFANT' : 'ADULT')
    setIdType(p.identityType || 'CITIZEN_ID')
    setIdNumber(p.identityNumber || '')
    setIdExpiry(p.identityExpiry || '')
    setEditingId(p.id)
    setFormError(null)
    setViewMode('edit')
  }

  const handleDelete = async (id: number) => {
    if (!confirm('Are you sure you want to remove this traveler from your directory?')) return
    try {
      await api.deletePassenger(id)
      setPassengers(prev => prev.filter(p => p.id !== id))
    } catch (err: any) {
      alert(err.message || 'Failed to delete traveler')
    }
  }

  const handleSaveForm = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!fullName.trim()) {
      setFormError('Please enter the full name.')
      return
    }
    if (!dob) {
      setFormError('Please select date of birth.')
      return
    }

    if (idType === 'CITIZEN_ID' && idNumber.trim() && !/^\d+$/.test(idNumber.trim())) {
      setFormError('National ID must contain digits only.')
      return
    }

    if (idType === 'PASSPORT' && idNumber.trim() && !idExpiry) {
      setFormError('Please enter passport expiry date.')
      return
    }

    // Determine passenger type accurately from DOB
    const inferredType = calculatePaxType(dob)
    const mappedPaxType: PassengerType = 
      inferredType === 'Child' ? 'CHILD' : inferredType === 'Infant' ? 'INFANT' : 'ADULT'

    const payload: PassengerRequest = {
      fullName: fullName.trim().toUpperCase(),
      dateOfBirth: dob,
      gender,
      nationality: nationality.trim() || 'Vietnam',
      passengerType: mappedPaxType,
      identityType: idNumber.trim() ? idType : undefined,
      identityNumber: idNumber.trim() ? idNumber.trim().toUpperCase() : undefined,
      identityExpiry: idType === 'PASSPORT' && idExpiry ? idExpiry : undefined,
    }

    setSubmitting(true)
    setFormError(null)
    try {
      if (viewMode === 'edit' && editingId) {
        const updated = await api.updatePassenger(editingId, payload)
        setPassengers(prev => prev.map(p => p.id === editingId ? updated : p))
      } else {
        const created = await api.createPassenger(payload)
        setPassengers(prev => [created, ...prev])
      }
      setViewMode('list')
    } catch (err: any) {
      console.error(err)
      setFormError(err.message || 'Failed to save traveler')
    } finally {
      setSubmitting(false)
    }
  }

  // Filter passengers if targetType is set
  const filteredPassengers = passengers.filter(p => {
    if (!targetType) return true
    const type = calculatePaxType(p.dateOfBirth)
    return type === targetType
  })

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
      role="dialog"
      aria-modal="true"
    >
      <div className="relative w-full max-w-2xl rounded-3xl bg-white shadow-2xl border border-slate-100 overflow-hidden flex flex-col max-h-[88vh]">
        {/* Header */}
        <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50/70">
          <div className="flex items-center gap-3">
            {viewMode !== 'list' ? (
              <button 
                type="button" 
                onClick={() => setViewMode('list')}
                className="size-9 rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-200/60 transition-colors cursor-pointer"
              >
                <ArrowLeft className="size-5" />
              </button>
            ) : (
              <div className="flex size-10 items-center justify-center rounded-2xl bg-[#102a56] text-white shadow-sm">
                <Users className="size-5" />
              </div>
            )}
            <div>
              <h2 className="text-xl font-bold text-[#102a56]">
                {viewMode === 'add' ? 'Add New Traveler' : viewMode === 'edit' ? 'Edit Traveler' : 'Saved Travelers'}
              </h2>
              <p className="text-xs text-slate-500 font-medium">
                {viewMode === 'list' 
                  ? onSelectPassenger
                    ? `Select a traveler ${targetType ? `(${targetType})` : ''} to autofill details`
                    : 'Manage your saved travelers for faster bookings'
                  : 'Securely save identity details for your account'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {viewMode === 'list' && (
              <button
                type="button"
                onClick={handleOpenAdd}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#102a56] text-white text-xs font-bold hover:bg-[#1a3d75] transition-all shadow-xs cursor-pointer"
              >
                <Plus className="size-4" />
                <span>Add Traveler</span>
              </button>
            )}
            <button
              onClick={onClose}
              className="size-9 flex items-center justify-center rounded-full text-slate-400 hover:bg-slate-200/60 hover:text-slate-700 transition-colors cursor-pointer"
              aria-label="Close"
            >
              <X className="size-5" />
            </button>
          </div>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto flex-1">
          {viewMode === 'list' ? (
            /* LIST VIEW */
            <div>
              {loading ? (
                <div className="py-16 flex flex-col items-center justify-center text-slate-400">
                  <Loader2 className="size-8 animate-spin mb-3 text-[#102a56]" />
                  <p className="text-sm font-semibold">Loading directory...</p>
                </div>
              ) : error ? (
                <div className="p-4 rounded-2xl bg-red-50 border border-red-200 text-red-600 text-xs font-medium flex items-center gap-2">
                  <AlertCircle className="size-4 shrink-0" />
                  <span>{error}</span>
                </div>
              ) : passengers.length === 0 ? (
                <div className="py-14 text-center">
                  <div className="size-16 rounded-3xl bg-blue-50 text-[#102a56] flex items-center justify-center mx-auto mb-4 border border-blue-100">
                    <Users className="size-8" />
                  </div>
                  <h3 className="font-bold text-slate-800 text-base mb-1">No saved travelers in your directory</h3>
                  <p className="text-xs text-slate-500 max-w-sm mx-auto mb-5 leading-relaxed">
                    Add friends or family now to save time on your upcoming flight bookings.
                  </p>
                  <button
                    type="button"
                    onClick={handleOpenAdd}
                    className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#102a56] text-white text-xs font-bold hover:bg-[#1a3d75] shadow-sm cursor-pointer transition-all"
                  >
                    <Plus className="size-4" />
                    <span>Add First Traveler</span>
                  </button>
                </div>
              ) : (
                <div className="space-y-3.5">
                  {filteredPassengers.length === 0 && targetType && (
                    <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs font-medium mb-3">
                      No saved travelers matching age category <strong>{targetType}</strong>. You can click <strong>"Add Traveler"</strong> above to add one.
                    </div>
                  )}

                  {filteredPassengers.map((pax) => {
                    const category = calculatePaxType(pax.dateOfBirth)
                    const initials = pax.fullName
                      .split(' ')
                      .map(n => n[0])
                      .slice(0, 2)
                      .join('')
                      .toUpperCase()

                    return (
                      <div 
                        key={pax.id}
                        className="flex flex-col sm:flex-row sm:items-center justify-between p-4 rounded-2xl border border-slate-200 bg-white hover:border-slate-300 hover:shadow-md transition-all gap-4 group"
                      >
                        <div className="flex items-start gap-3.5">
                          <div className="size-11 rounded-2xl bg-slate-100 text-[#102a56] font-bold text-sm flex items-center justify-center shrink-0 border border-slate-200/80 group-hover:bg-[#102a56] group-hover:text-white transition-colors">
                            {initials}
                          </div>
                          <div>
                            <div className="flex items-center gap-2 flex-wrap">
                              <h4 className="font-bold text-slate-900 text-sm tracking-tight uppercase">
                                {pax.fullName}
                              </h4>
                              <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                                category === 'Adult' ? 'bg-blue-50 text-blue-700' : category === 'Child' ? 'bg-amber-50 text-amber-700' : 'bg-purple-50 text-purple-700'
                              }`}>
                                {category}
                              </span>
                              <span className="text-[11px] font-medium text-slate-400">
                                ({pax.gender === 'MALE' ? 'Male' : pax.gender === 'FEMALE' ? 'Female' : 'Other'})
                              </span>
                            </div>

                            <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
                              <div className="flex items-center gap-1">
                                <Calendar className="size-3 text-slate-400" />
                                <span>{pax.dateOfBirth}</span>
                              </div>
                              <div className="flex items-center gap-1">
                                <Globe className="size-3 text-slate-400" />
                                <span>{pax.nationality || 'Vietnam'}</span>
                              </div>
                              {pax.identityNumber && (
                                <div className="flex items-center gap-1 font-mono text-[11px] text-slate-700 bg-slate-100 px-2 py-0.5 rounded-md">
                                  <CreditCard className="size-3 text-slate-400" />
                                  <span>{pax.identityType === 'PASSPORT' ? 'Passport' : 'ID'}: {pax.identityNumber}</span>
                                </div>
                              )}
                            </div>
                          </div>
                        </div>

                        {/* Actions */}
                        <div className="flex items-center gap-2 sm:self-center shrink-0 border-t sm:border-t-0 pt-2 sm:pt-0 border-slate-100">
                          {onSelectPassenger && (
                            <button
                              type="button"
                              onClick={() => {
                                onSelectPassenger(pax)
                                onClose()
                              }}
                              className="px-3.5 py-1.5 rounded-xl bg-emerald-600 text-white text-xs font-bold hover:bg-emerald-700 transition-colors cursor-pointer flex items-center gap-1.5 shadow-2xs"
                            >
                              <Check className="size-3.5" />
                              <span>Select</span>
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => handleOpenEdit(pax)}
                            className="size-8 rounded-xl flex items-center justify-center text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors cursor-pointer"
                            title="Edit"
                          >
                            <Edit2 className="size-3.5" />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDelete(pax.id)}
                            className="size-8 rounded-xl flex items-center justify-center text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors cursor-pointer"
                            title="Delete"
                          >
                            <Trash2 className="size-3.5" />
                          </button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          ) : (
            /* ADD / EDIT FORM */
            <form onSubmit={handleSaveForm} className="space-y-4">
              {formError && (
                <div className="p-3.5 rounded-2xl bg-red-50 border border-red-200 text-red-600 text-xs font-medium flex items-center gap-2 animate-in fade-in">
                  <AlertCircle className="size-4 shrink-0" />
                  <span>{formError}</span>
                </div>
              )}

              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                  Full name (as on ID / Passport) <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                  <input
                    type="text"
                    required
                    placeholder="e.g. NGUYEN VAN A"
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value.toUpperCase())}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 pl-10 pr-4 text-sm font-semibold uppercase text-slate-900 focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                  />
                </div>
                <p className="text-[10px] text-slate-400 mt-1 ml-1 uppercase">Without accents, matching official identification documents</p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    Date of birth <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <input
                      type="date"
                      required
                      max={new Date().toISOString().split('T')[0]}
                      min="1900-01-01"
                      value={dob}
                      onChange={(e) => setDob(e.target.value)}
                      className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 px-3 text-sm text-slate-900 focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10 cursor-pointer"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    Gender <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={gender}
                    onChange={(e) => setGender(e.target.value as Gender)}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 px-3 text-sm text-slate-900 focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10 cursor-pointer"
                  >
                    <option value="MALE">Male</option>
                    <option value="FEMALE">Female</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    Nationality <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <Globe className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                    <input
                      type="text"
                      required
                      placeholder="Vietnam"
                      value={nationality}
                      onChange={(e) => setNationality(e.target.value)}
                      className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 pl-10 pr-4 text-sm text-slate-900 focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    Document Type
                  </label>
                  <select
                    value={idType}
                    onChange={(e) => setIdType(e.target.value as DocumentType)}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 px-3 text-sm text-slate-900 focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10 cursor-pointer"
                  >
                    <option value="CITIZEN_ID">National ID Card (CCCD)</option>
                    <option value="PASSPORT">Passport</option>
                  </select>
                </div>
              </div>

              <div className={`grid ${idType === 'PASSPORT' ? 'grid-cols-1 sm:grid-cols-2' : 'grid-cols-1'} gap-4`}>
                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    {idType === 'PASSPORT' ? 'Passport Number' : 'National ID Number'}
                  </label>
                  <div className="relative">
                    <CreditCard className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                    <input
                      type="text"
                      placeholder={idType === 'PASSPORT' ? 'e.g. C1234567' : 'e.g. 079195001234'}
                      value={idNumber}
                      onChange={(e) => setIdNumber(idType === 'CITIZEN_ID' ? e.target.value.replace(/\D/g, '') : e.target.value.toUpperCase())}
                      className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 pl-10 pr-4 text-sm font-mono text-slate-900 focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10"
                    />
                  </div>
                </div>

                {idType === 'PASSPORT' && (
                  <div>
                    <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                      Passport Expiry Date <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="date"
                      min={new Date().toISOString().split('T')[0]}
                      value={idExpiry}
                      onChange={(e) => setIdExpiry(e.target.value)}
                      className="w-full rounded-xl border border-slate-200 bg-slate-50/50 py-2.5 px-3 text-sm text-slate-900 focus:border-[#102a56] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#102a56]/10 cursor-pointer"
                    />
                  </div>
                )}
              </div>

              <div className="pt-4 flex items-center justify-end gap-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setViewMode('list')}
                  className="px-4 py-2.5 rounded-xl border border-slate-200 text-xs font-bold text-slate-600 hover:bg-slate-50 transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-6 py-2.5 rounded-xl bg-[#102a56] text-xs font-bold text-white hover:bg-[#1a3d75] shadow-md shadow-blue-950/20 transition-all flex items-center gap-2 disabled:opacity-60 cursor-pointer"
                >
                  {submitting ? (
                    <>
                      <Loader2 className="size-4 animate-spin" />
                      <span>Saving...</span>
                    </>
                  ) : (
                    <span>Save Traveler</span>
                  )}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
