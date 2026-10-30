import type { PassengerRequest, PassengerResponse, BookingResponse, PageResponse } from '@/types/api'
import type { ApiRequest } from '@/lib/http'

export function createDomainApi(request: ApiRequest) {
  return {
    async getMyPassengers(): Promise<PassengerResponse[]> {
      return request<PassengerResponse[]>('GET', '/passengers/my')
    },

    async createPassenger(payload: PassengerRequest): Promise<PassengerResponse> {
      return request<PassengerResponse>('POST', '/passengers', payload)
    },

    async updatePassenger(id: number, payload: PassengerRequest): Promise<PassengerResponse> {
      return request<PassengerResponse>('PUT', `/passengers/${id}`, payload)
    },

    async deletePassenger(id: number): Promise<void> {
      return request<void>('DELETE', `/passengers/${id}`)
    },

    async getMyBookings(page = 0, size = 50): Promise<PageResponse<BookingResponse>> {
      return request<PageResponse<BookingResponse>>('GET', `/bookings/my?page=${page}&size=${size}`)
    },

    async getBookingById(id: number): Promise<BookingResponse> {
      return request<BookingResponse>('GET', `/bookings/${id}`)
    },

    async cancelBooking(id: number): Promise<void> {
      return request<void>('POST', `/bookings/${id}/cancel`, {})
    },
  }
}
