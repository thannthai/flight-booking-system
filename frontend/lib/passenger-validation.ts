import { passengerType, type PassengerDraft, type PassengerForm, type PassengerSlot } from '@/lib/checkout-model'
import type { PassengerType } from '@/types/api'

// Matches BookingService's OffsetDateTime.toLocalDate(), not the browser calendar.
export function departureDate(departure: string): string { return departure.slice(0, 10) }
export function passengerTypeAtDate(dob: string, date: string): PassengerType | null {
  const valid = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value
  if (!valid(dob) || !valid(date) || dob > date) return null
  const years = Number(date.slice(0, 4)) - Number(dob.slice(0, 4)) - (date.slice(5) < dob.slice(5) ? 1 : 0)
  return years >= 12 ? 'ADULT' : years >= 2 ? 'CHILD' : 'INFANT'
}
export function documentFields(form: Pick<PassengerForm, 'id' | 'idType' | 'idExpiry'>) {
  const number = form.id.trim()
  return { identityType: number ? form.idType || undefined : undefined, identityNumber: number || undefined, identityExpiry: number && form.idType === 'PASSPORT' && form.idExpiry ? form.idExpiry : undefined }
}
export function validatePassengers(forms: PassengerForm[], types: PassengerType[], date: string): Record<string, string> {
  const errors: Record<string, string> = {}, documents = new Set<string>(), guardians = new Set<number>()
  const key = (form: PassengerForm) => form.idType && form.id.trim() ? `${form.idType}_${form.id.trim().toUpperCase()}` : null
  forms.forEach((form, index) => {
    if (!form.name.trim()) errors[`pax_${index}_name`] = 'Full name is required'
    if (passengerTypeAtDate(form.dob, date) !== types[index]) errors[`pax_${index}_dob`] = `Date of birth must match ${types[index].toLowerCase()} age on departure (${date})`
    const document = key(form)
    if (form.id.trim() && !['CITIZEN_ID','PASSPORT'].includes(form.idType)) errors[`pax_${index}_id`] = 'Choose a document type for this number'
    if (types[index] === 'ADULT' && !document) errors[`pax_${index}_id`] = 'An identity document is required for adults'
    if (types[index] !== 'INFANT' && document) {
      if (documents.has(document)) errors[`pax_${index}_id`] = 'This document is already used by another seated passenger'
      documents.add(document)
    }
    if (types[index] === 'INFANT') {
      const guardian = form.guardianIndex
      if (!Number.isInteger(guardian) || guardian == null || types[guardian] !== 'ADULT' || guardians.has(guardian)) errors[`pax_${index}_guardian`] = 'Select a distinct adult guardian for each infant'
      else {
        guardians.add(guardian)
        if (document && document !== key(forms[guardian])) errors[`pax_${index}_id`] = 'An infant document must match the accompanying adult document'
      }
    }
  })
  return errors
}
export function documentRemovalUnsupported(existingNumber: string | undefined, nextNumber: string): boolean { return !!existingNumber?.trim() && !nextNumber.trim() }

export function validatePassengerDraft(draft: PassengerDraft, slots: PassengerSlot[], date: string): Record<string, string> {
  const errors: Record<string, string> = {}
  const { contactInfo } = draft
  if (!contactInfo.name.trim()) errors.contact_name = 'Contact full name is required'
  if (!contactInfo.email.trim()) errors.contact_email = 'Email address is required'
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contactInfo.email.trim())) errors.contact_email = 'Please enter a valid email address'
  if (!contactInfo.phone.trim()) errors.contact_phone = 'Phone number is required'
  else if (contactInfo.phone.replace(/\D/g, '').length < 8) errors.contact_phone = 'Phone number must be at least 8 digits'
  return { ...errors, ...validatePassengers(draft.passengers, slots.map(p => passengerType(p.type)), date) }
}
