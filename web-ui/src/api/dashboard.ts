import client from './client'
import type {
  DashboardStats,
  StatusDistribution,
  DelayedProject,
  RecentEvent
} from '@/types'

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
  }
}
