import client from './client'
import type { ProjectListItem, PageResult, ProjectOverview, BranchComparison } from '@/types'

export const projectsApi = {
  getProjects: (params: {
    status?: string
    syncMethod?: string
    group?: string
    delayRange?: string
    search?: string
    sortBy?: string
    sortOrder?: string
    page?: number
    size?: number
  }) => {
    return client.get<any, { data: PageResult<ProjectListItem> }>('/sync/projects', { params })
  },

  getGroups: () => {
    return client.get<any, { data: string[] }>('/sync/projects/groups')
  },

  getProjectOverview: (id: number) => {
    return client.get<any, { data: ProjectOverview }>(`/sync/projects/${id}/overview`)
  },

  batchSync: (projectIds: number[]) => {
    return client.post('/sync/projects/batch-sync', { projectIds })
  },

  batchPause: (projectIds: number[]) => {
    return client.post('/sync/projects/batch-pause', { projectIds })
  },

  batchResume: (projectIds: number[]) => {
    return client.post('/sync/projects/batch-resume', { projectIds })
  },

  batchDelete: (projectIds: number[]) => {
    return client.post('/sync/projects/batch-delete', { projectIds })
  },

  getBranchComparison: (params: { syncProjectId?: number; projectKey?: string }) => {
    return client.get<any, { data: BranchComparison }>('/sync/branches', { params })
  }
}
