import client from './client'
import type { EventListItem, PageResult, EventStats, EventDetails } from '@/types'

export const eventsApi = {
  getEvents: (params: {
    projectId?: number
    eventType?: string
    status?: string
    startDate?: string
    endDate?: string
    search?: string
    page?: number
    size?: number
  }) => {
    return client.get<any, { data: PageResult<EventListItem> }>('/sync/events', { params })
  },

  getEventStats: (date?: string) => {
    return client.get<any, { data: EventStats }>('/sync/events/stats', {
      params: { date }
    })
  },

  getEventDetails: (id: number) => {
    return client.get<any, { data: EventDetails }>(`/sync/events/${id}/details`)
  }
}
