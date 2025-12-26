import client from './client'
import type { EventListItem, PageResult, EventStats, EventDetails } from '@/types'

export interface EventDetailEnhanced {
  id: number
  syncProjectId: number
  projectKey: string
  eventType: string
  eventSource: string
  status: string
  eventTime: string
  durationSeconds: number
  commitSha: string
  ref: string
  branchName: string
  errorMessage: string
  eventData: any
  totalBranches: number
  recentBranches: BranchInfo[]
}

export interface BranchInfo {
  branchName: string
  commitSha: string
  commitMessage: string
  commitAuthor: string
  committedAt: string
  isDefault: boolean
  isProtected: boolean
}

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
  },

  getEventDetail: (id: number) => {
    return client.get<any, { data: EventDetailEnhanced }>(`/sync/events/${id}/detail`)
  }
}
