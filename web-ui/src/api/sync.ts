import client from './client'
import type { ApiResponse, PageResult } from '@/types'

export interface ScanResult {
  type: string
  startTime: string
  endTime: string
  scannedCount: number
  addedCount: number
  updatedCount: number
  removedCount: number
  failedCount: number
}

export interface SyncResult {
  id: number
  syncProjectId: number
  projectKey: string
  syncMethod: string
  lastSyncAt: string
  startedAt: string
  completedAt: string
  syncStatus: 'success' | 'failed' | 'skipped'
  hasChanges: boolean
  changesCount: number
  sourceCommitSha: string
  targetCommitSha: string
  durationSeconds: number
  errorMessage: string
  summary: string
}

export interface SyncResultDetail extends SyncResult {
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

export interface SyncResultQuery {
  syncStatus?: string
  search?: string
  page?: number
  size?: number
}

export const syncApi = {
  /**
   * Trigger manual scan
   */
  triggerScan(type: 'incremental' | 'full' = 'incremental'): Promise<ApiResponse<ScanResult>> {
    return client.post('/sync/scan', null, {
      params: { type }
    })
  },

  /**
   * Get sync results list
   */
  getSyncResults(query: SyncResultQuery = {}): Promise<ApiResponse<PageResult<SyncResult>>> {
    return client.get('/sync/results', {
      params: query
    })
  },

  /**
   * Get sync events for a project
   */
  getEvents(params: {
    projectId?: number
    eventType?: string
    status?: string
    startDate?: string
    endDate?: string
    search?: string
    page?: number
    size?: number
  }): Promise<ApiResponse<PageResult<any>>> {
    return client.get('/sync/events', { params })
  },

  /**
   * Get sync result detail
   */
  getSyncResultDetail(id: number): Promise<ApiResponse<SyncResultDetail>> {
    return client.get(`/sync/results/${id}`)
  }
}
