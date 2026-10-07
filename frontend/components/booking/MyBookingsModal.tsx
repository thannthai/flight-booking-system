'use client'

import React, { useState, useEffect } from 'react'
import { X, Plane, Calendar, Clock, AlertCircle, RefreshCw, Ticket, CheckCircle2, ChevronRight, Ban } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import type { BookingResponse } from '@/types/api'
import { formatTime, formatVND } from '@/lib/format'
import { BoardingPassModal } from '@/components/booking/BoardingPassModal'

interface MyBookingsModalProps {
  isOpen: boolean
  onClose: () => void
  onPayBooking?: (booking: BookingResponse) => void
}

export function MyBookingsModal({ isOpen, onClose, onPayBooking }: MyBookingsModalProps) {
  const [bookings, setBookings] = useState<BookingResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'ALL' | 'COMPLETED' | 'PENDING' | 'CANCELLED'>('ALL')
  
  // Selected booking for Boarding Pass Modal
  const [boardingPassBooking, setBoardingPassBooking] = useState<BookingResponse | null>(null)
  const [cancellingId, setCancellingId] = useState<number | null>(null)

  const fetchBookings = () => {
    setLoading(true)
    setError(null)
    api.getMyBookings(0, 50)
      .then(res => {
        setBookings(res.content || [])
        setLoading(false)
      })
      .catch(err => {
        console.error('Failed to load bookings:', err)
        setError(err.message || 'Could not load your bookings')
        setLoading(false)
      })
  }

  useEffect(() => {
    if (isOpen) {
      fetchBookings()
    }
  }, [isOpen])

  if (!isOpen) return null

  const handleCancelBooking = async (bookingId: number) => {
    if (!window.confirm('Are you sure you want to cancel this pending booking? Held seats will be released immediately.')) {
      return
    }

    setCancellingId(bookingId)
    try {
      await api.cancelBooking(bookingId)
      fetchBookings()
    } catch (err: any) {
      alert(err.message || 'Failed to cancel booking')
    } finally {
      setCancellingId(null)
    }
  }

  const filteredBookings = bookings.filter(b => {
    if (activeTab === 'ALL') return true
    return b.status === activeTab
  })

  return (
    <>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-950/60 backdrop-blur-sm overflow-y-auto">
        <div className="relative w-full max-w-4xl max-h-[90vh] flex flex-col bg-[#f7f8fa] rounded-3xl shadow-2xl border border-slate-200 overflow-hidden">
          
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-5 bg-white border-b border-slate-200">
            <div className="flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-xl bg-[#102a56] text-white">
                <Ticket className="size-5" />
              </span>
              <div>
                <h2 className="text-lg font-bold text-slate-900">My Bookings & E-Tickets</h2>
                <p className="text-xs text-slate-500">Manage your flight reservations, view passes, and pay</p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={fetchBookings}
                className="size-9 flex items-center justify-center rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
                title="Refresh bookings"
              >
                <RefreshCw className={`size-4.5 ${loading ? 'animate-spin text-[#102a56]' : ''}`} />
              </button>
              <button
                type="button"
                onClick={onClose}
                className="size-9 flex items-center justify-center rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <X className="size-5" />
              </button>
            </div>
          </div>

          {/* Filter Tabs */}
          <div className="flex flex-wrap items-center gap-2 sm:gap-3 px-6 py-4 bg-white border-b border-slate-200">
            {(['ALL', 'COMPLETED', 'PENDING', 'CANCELLED'] as const).map(tab => (
              <button
                key={tab}
                type="button"
                onClick={() => setActiveTab(tab)}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer ${
                  activeTab === tab
                    ? 'bg-[#102a56] text-white shadow-xs'
                    : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {tab === 'ALL' ? 'All Bookings' : tab === 'COMPLETED' ? 'Confirmed' : tab === 'PENDING' ? 'Pending Payment' : 'Cancelled'}
              </button>
            ))}
          </div>

          {/* Booking List Body */}
          <div className="flex-1 overflow-y-auto p-6 sm:p-8">
            <div className="flex flex-col gap-5 sm:gap-6">
            {loading ? (
              <div className="py-24 flex flex-col items-center justify-center gap-3 text-slate-400">
                <RefreshCw className="size-8 animate-spin text-[#102a56]" />
                <p className="text-xs font-semibold">Loading your bookings...</p>
              </div>
            ) : error ? (
              <div className="py-20 text-center">
                <AlertCircle className="size-10 text-red-400 mx-auto mb-2" />
                <p className="text-sm font-bold text-red-600">{error}</p>
                <Button onClick={fetchBookings} variant="outline" className="mt-4 text-xs">
                  Try Again
                </Button>
              </div>
            ) : filteredBookings.length === 0 ? (
              <div className="py-20 text-center bg-white rounded-2xl border border-slate-200/80 p-8 shadow-xs">
                <Plane className="size-12 text-slate-300 mx-auto mb-3" />
                <h3 className="text-base font-bold text-slate-800">No bookings found</h3>
                <p className="text-xs text-slate-500 max-w-sm mx-auto mt-1 mb-6">
                  {activeTab === 'ALL'
                    ? "You haven't made any flight bookings yet. Search for a destination to plan your trip!"
                    : `No bookings found with status ${activeTab.toLowerCase()}.`}
                </p>
                <Button onClick={onClose} className="rounded-xl bg-[#102a56] hover:bg-slate-800 text-xs font-bold px-6">
                  Book a Flight Now
                </Button>
              </div>
            ) : (
              filteredBookings.map((b) => {
                const firstTicket = b.tickets?.[0]
                const flight = firstTicket?.flight
                const statusColor = b.status === 'COMPLETED'
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-100'
                  : b.status === 'PENDING'
                  ? 'bg-amber-50 text-amber-700 border-amber-100'
                  : 'bg-slate-100 text-slate-600 border-slate-200'

                const paxCount = b.tickets?.length || b.passengers?.length || 1

                return (
                  <div
                    key={b.id}
                    className="bg-white rounded-2xl p-6 sm:p-7 border border-slate-200 shadow-xs hover:shadow-md transition-all flex flex-col gap-5"
                  >
                    {/* Top row: PNR, status, and created date */}
                    <div className="flex flex-wrap items-center justify-between gap-3 pb-3.5 border-b border-slate-100">
                      <div className="flex items-center gap-3">
                        <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">PNR</span>
                        <span className="font-mono text-lg font-black text-[#102a56] tracking-wider">{b.pnr}</span>
                        <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold border uppercase ${statusColor}`}>
                          {b.status}
                        </span>
                      </div>
                      <div className="text-xs text-slate-400">
                        Booked on {new Date(b.createdAt).toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' })}
                      </div>
                    </div>

                    {/* Middle row: Flight Route and Passenger details */}
                    <div className="grid sm:grid-cols-[1fr_auto] gap-4 items-center">
                      <div className="flex flex-col gap-3">
                        {flight ? (
                          <div className="flex items-center gap-4">
                            <div className="size-10 bg-slate-50 rounded-xl p-1 border border-slate-100 flex items-center justify-center shrink-0">
                              <img
                                src={`/airlines/${flight.airline?.code}.svg`}
                                alt={flight.airline?.name}
                                className="h-full w-full object-contain"
                                onError={(e) => { (e.target as HTMLElement).style.display = 'none' }}
                              />
                            </div>
                            <div>
                              <p className="font-bold text-slate-900 text-sm">
                                {flight.airline?.name} · {flight.flightNo}
                              </p>
                              <div className="flex items-center gap-2 text-xs text-slate-600 font-semibold mt-0.5">
                                <span>{flight.origin?.city} ({flight.origin?.iataCode})</span>
                                <span className="text-slate-400">➔</span>
                                <span>{flight.dest?.city} ({flight.dest?.iataCode})</span>
                                <span className="text-slate-300">•</span>
                                <span className="text-slate-500 font-normal">
                                  {formatTime(flight.departureTime)} - {formatTime(flight.arrivalTime)}
                                </span>
                              </div>
                            </div>
                          </div>
                        ) : (
                          <p className="text-sm font-semibold text-slate-700">Flight Details Processing</p>
                        )}

                        <div className="flex items-center gap-4 text-xs text-slate-500">
                          <span>
                            <strong>{paxCount}</strong> {paxCount === 1 ? 'Passenger' : 'Passengers'}
                          </span>
                          <span>•</span>
                          <span>
                            Contact: <strong>{b.contactName}</strong>
                          </span>
                        </div>
                      </div>

                      {/* Total Amount */}
                      <div className="sm:text-right">
                        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Total Amount</span>
                        <strong className="text-xl sm:text-2xl font-black text-[#102a56] tabular-nums">
                          {formatVND(b.totalAmount)}
                        </strong>
                      </div>
                    </div>

                    {/* Bottom Actions or Status Info */}
                    {b.status === 'CANCELLED' ? (
                      <div className="flex flex-wrap items-center justify-between text-xs text-slate-400 pt-3.5 border-t border-slate-100">
                        <span className="flex items-center gap-1.5 text-slate-500 font-medium">
                          <Ban className="size-3.5 text-slate-400" />
                          Booking cancelled · Seats released
                        </span>
                        <span className="text-[11px] text-slate-400">
                          {(b as any).cancelReason || 'Cancelled'}
                        </span>
                      </div>
                    ) : (
                      <div className="flex flex-wrap items-center justify-end gap-3 pt-3.5 border-t border-slate-100">
                        {b.status === 'COMPLETED' && (
                          <Button
                            type="button"
                            onClick={() => setBoardingPassBooking(b)}
                            className="h-10 px-5 rounded-xl bg-[#102a56] hover:bg-slate-800 text-white font-bold text-xs cursor-pointer flex items-center gap-2"
                          >
                            <Ticket className="size-4" />
                            <span>View Boarding Pass</span>
                          </Button>
                        )}

                        {b.status === 'PENDING' && (
                          <>
                            <Button
                              type="button"
                              variant="outline"
                              disabled={cancellingId === b.id}
                              onClick={() => handleCancelBooking(b.id)}
                              className="h-10 px-4 rounded-xl border-red-200 text-red-600 hover:bg-red-50 font-bold text-xs cursor-pointer flex items-center gap-1.5"
                            >
                              <Ban className="size-3.5" />
                              <span>{cancellingId === b.id ? 'Cancelling...' : 'Cancel Booking'}</span>
                            </Button>
                            {onPayBooking && (
                              <Button
                                type="button"
                                onClick={() => {
                                  onClose()
                                  onPayBooking(b)
                                }}
                                className="h-10 px-5 rounded-xl bg-[#f59e0b] hover:bg-[#d97706] text-[#102a56] font-bold text-xs cursor-pointer flex items-center gap-2"
                              >
                                <span>Complete Payment</span>
                                <ChevronRight className="size-4" />
                              </Button>
                            )}
                          </>
                        )}
                      </div>
                    )}
                  </div>
                )
              })
            )}
            </div>
          </div>

          {/* Footer */}
          <div className="px-6 py-4 bg-white border-t border-slate-200 flex justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              className="h-10 px-6 rounded-xl font-bold text-xs cursor-pointer"
            >
              Close
            </Button>
          </div>

        </div>
      </div>

      {/* Boarding Pass Modal Child */}
      <BoardingPassModal
        isOpen={Boolean(boardingPassBooking)}
        onClose={() => setBoardingPassBooking(null)}
        booking={boardingPassBooking}
      />
    </>
  )
}
