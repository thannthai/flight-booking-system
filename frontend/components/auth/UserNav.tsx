'use client'

import { useState, useRef, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { DemoGuide } from '@/components/DemoGuide'
import { useAuth } from '@/lib/auth-context'
import { LogOut, ChevronDown, ShieldCheck, Mail, Phone, Users, Ticket } from 'lucide-react'
import { PassengerDirectoryModal } from '@/components/passenger/PassengerDirectoryModal'
import { MyBookingsModal } from '@/components/booking/MyBookingsModal'

export function UserNav() {
  const router = useRouter()
  const { user, isAuthenticated, openAuthModal, logout, authError } = useAuth()
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [passengerModalOpen, setPassengerModalOpen] = useState(false)
  const [myBookingsOpen, setMyBookingsOpen] = useState(false)
  const privateOwner = useRef(user?.id)
  if (privateOwner.current !== user?.id) {
    privateOwner.current = user?.id
    if (passengerModalOpen) setPassengerModalOpen(false)
    if (myBookingsOpen) setMyBookingsOpen(false)
  }
  const menuRef = useRef<HTMLDivElement>(null)

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  if (!isAuthenticated || !user) {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <DemoGuide />
        {authError && <p role="alert" className="text-xs text-red-700">{authError}</p>}
        <button
          type="button"
          onClick={() => openAuthModal('login')}
          className="cursor-pointer rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-bold text-slate-700 transition-all hover:bg-slate-50 hover:border-slate-300 shadow-2xs"
        >
          Sign In
        </button>
        <button
          type="button"
          onClick={() => openAuthModal('register')}
          className="cursor-pointer rounded-full bg-[#102a56] px-4 py-2 text-xs font-bold text-white shadow-sm transition-all hover:bg-[#1a3d75] hover:shadow"
        >
          Register
        </button>
      </div>
    )
  }

  // User is logged in
  const initials = user.demoAlias ? 'D' : user.fullName
    ? user.fullName
        .split(' ')
        .map((n) => n[0])
        .slice(0, 2)
        .join('')
        .toUpperCase()
    : 'U'

  return (
    <>
      <div className="relative" ref={menuRef}>
        <button
          type="button"
          onClick={() => setDropdownOpen(!dropdownOpen)}
          className="flex items-center gap-2.5 rounded-full border border-slate-200 bg-white py-1.5 pl-2 pr-3.5 shadow-2xs hover:bg-slate-50 hover:border-slate-300 transition-all cursor-pointer"
          aria-expanded={dropdownOpen}
        >
          <div className="flex size-7 items-center justify-center rounded-full bg-[#102a56] text-white text-xs font-bold shadow-xs">
            {initials}
          </div>
          <div className="text-left hidden sm:block">
            <p className="text-xs font-bold text-slate-800 leading-tight truncate max-w-[130px]">
              {user.demoAlias ? 'Demo Traveler' : user.fullName || user.email}
            </p>
          </div>
          <ChevronDown className="size-3.5 text-slate-400" />
        </button>

        {dropdownOpen && (
          <div className="absolute right-0 mt-2 w-64 rounded-2xl bg-white p-2 shadow-xl border border-slate-100 z-50 animate-in fade-in zoom-in-95 duration-150">
            <div className="px-3 py-2.5 border-b border-slate-100">
              <p className="text-xs font-bold text-slate-900 truncate">{user.demoAlias ? 'Demo Traveler' : user.fullName}</p>
              <div className="mt-1 flex items-center gap-1.5 text-[11px] text-slate-500 truncate">
                <Mail className="size-3 shrink-0 text-slate-400" />
                <span className="truncate">{user.email}</span>
              </div>
              {user.phone && (
                <div className="mt-0.5 flex items-center gap-1.5 text-[11px] text-slate-500">
                  <Phone className="size-3 shrink-0 text-slate-400" />
                  <span>{user.phone}</span>
                </div>
              )}
              <div className="mt-2 inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-bold text-blue-700">
                <ShieldCheck className="size-3" />
                <span>{user.role === 'ROLE_ADMIN' ? 'Administrator' : 'Skyway Traveler'}</span>
              </div>
            </div>

            <div className="py-1 border-b border-slate-100">
              <button
                type="button"
                onClick={() => {
                  setDropdownOpen(false)
                  setMyBookingsOpen(true)
                }}
                className="w-full flex items-center gap-2.5 px-3 py-2 text-xs font-semibold text-slate-700 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer"
              >
                <Ticket className="size-3.5 text-slate-400" />
                <span>My Bookings & Passes</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setDropdownOpen(false)
                  setPassengerModalOpen(true)
                }}
                className="w-full flex items-center gap-2.5 px-3 py-2 text-xs font-semibold text-slate-700 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer"
              >
                <Users className="size-3.5 text-slate-400" />
                <span>Saved Travelers</span>
              </button>
            </div>

            <div className="pt-1">
              <button
                type="button"
                onClick={async () => {
                  setDropdownOpen(false)
                  try { await logout() } catch { /* AuthProvider displays the server sign-out warning. */ }
                }}
                className="w-full flex items-center gap-2.5 px-3 py-2 text-xs font-semibold text-red-600 rounded-xl hover:bg-red-50 transition-colors cursor-pointer"
              >
                <LogOut className="size-3.5" />
                <span>Sign Out</span>
              </button>
            </div>
          </div>
        )}
      </div>

      <PassengerDirectoryModal 
        isOpen={passengerModalOpen} 
        onClose={() => setPassengerModalOpen(false)} 
      />

      <MyBookingsModal 
        isOpen={myBookingsOpen} 
        onClose={() => setMyBookingsOpen(false)} 
        onPayBooking={booking => router.push(`/payment/status?bookingId=${booking.id}`)}
      />
    </>
  )
}
