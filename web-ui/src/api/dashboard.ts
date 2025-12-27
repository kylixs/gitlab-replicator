import client from './client'
import type {
  DashboardStats,
  StatusDistribution,
  DelayedProject,
  RecentEvent
} from '@/types'

export interface TodaySyncStats {
  totalSyncs: number
  successSyncs: number
  failedSyncs: number
  totalBranchChanges: number
}

export interface TrendData {
  dates: string[]
  totalSyncs: number[]
  successSyncs: number[]
  failedSyncs: number[]
}

export interface EventTypeTrend {
  dates: string[]
  typeData: Record<string, number[]>
}

export const dashboardApi = {
  getStats: () => {
    return client.get<any, { data: DashboardStats }>('/dashboard/stats')
  },

  getStatusDistribution: () => {
    return client.get<any, { data: StatusDistribution }>('/dashboard/status-distribution')
  },

  getTopDelayedProjects: (limit = 10) => {
    return client.get<any, { data: DelayedProject[] }>('/dashboard/top-delayed-projects', {
      params: { limit }
    })
  },

  getRecentEvents: (limit = 20) => {
    return client.get<any, { data: RecentEvent[] }>('/dashboard/recent-events', {
      params: { limit }
    })
  },

  getTodayStats: () => {
    return client.get<any, { data: TodaySyncStats }>('/dashboard/today-stats')
  },

  getTrend: (range: '7d' | '24h' = '7d') => {
    return client.get<any, { data: TrendData }>('/dashboard/trend', {
      params: { range }
    })
  },

  getEventTypeTrend: (range: '7d' | '24h' = '7d') => {
    return client.get<any, { data: EventTypeTrend }>('/dashboard/event-type-trend', {
      params: { range }
    })
  }
}
