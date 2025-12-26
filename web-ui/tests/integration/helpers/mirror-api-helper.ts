/**
 * GitLab Mirror API Helper for Integration Tests
 *
 * Provides utilities to interact with GitLab Mirror API for testing
 */

const MIRROR_API_URL = process.env.MIRROR_API_URL || 'http://localhost:9999/api'
const MIRROR_API_KEY = process.env.MIRROR_API_KEY || 'dev-api-key-12345'

export interface SyncProject {
  id: number
  projectKey: string
  syncStatus: string
  syncMethod: string
  lastSyncAt: string
  lastSyncStatus: string
  consecutiveFailures: number
}

export interface BranchComparison {
  projectKey: string
  syncProjectId: number
  sourceBranchCount: number
  targetBranchCount: number
  syncedCount: number
  outdatedCount: number
  missingInTargetCount: number
  extraInTargetCount: number
  branches: BranchInfo[]
}

export interface BranchInfo {
  branchName: string
  syncStatus: string
  sourceCommitId?: string
  sourceCommitShort?: string
  sourceLastCommitAt?: string
  sourceCommitAuthor?: string
  sourceCommitMessage?: string
  targetCommitId?: string
  targetCommitShort?: string
  targetLastCommitAt?: string
  targetCommitAuthor?: string
  targetCommitMessage?: string
  commitDiff?: number
}

export interface SyncResult {
  id: number
  syncProjectId: number
  projectKey: string
  syncMethod: string
  lastSyncAt: string
  startedAt: string
  completedAt: string
  syncStatus: string
  hasChanges: boolean
  changesCount?: number
  sourceCommitSha?: string
  targetCommitSha?: string
  durationSeconds: number
  errorMessage?: string
  summary?: string
}

export class MirrorApiHelper {
  private baseUrl: string
  private apiKey: string

  constructor() {
    this.baseUrl = MIRROR_API_URL
    this.apiKey = MIRROR_API_KEY
  }

  /**
   * Get project by key
   */
  async getProject(projectKey: string): Promise<SyncProject | null> {
    const response = await fetch(`${this.baseUrl}/sync/projects/${encodeURIComponent(projectKey)}`, {
      headers: {
        'Authorization': `Bearer ${this.apiKey}`
      }
    })

    if (response.status === 404) {
      return null
    }

    if (!response.ok) {
      throw new Error(`Failed to get project: ${response.statusText}`)
    }

    const result = await response.json()
    return result.success ? result.data : null
  }

  /**
   * Get all projects with filters
   */
  async getProjects(filters?: {
    group?: string
    status?: string
    syncMethod?: string
  }): Promise<{ items: SyncProject[], total: number }> {
    const params = new URLSearchParams()
    if (filters?.group) params.append('group', filters.group)
    if (filters?.status) params.append('status', filters.status)
    if (filters?.syncMethod) params.append('syncMethod', filters.syncMethod)

    const response = await fetch(`${this.baseUrl}/sync/projects?${params}`, {
      headers: {
        'Authorization': `Bearer ${this.apiKey}`
      }
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Failed to get projects (${response.status} ${response.statusText}): ${errorText}`)
    }

    const result = await response.json()
    return result.success ? result.data : { items: [], total: 0 }
  }

  /**
   * Trigger sync for a project
   */
  async triggerSync(projectId: number): Promise<void> {
    const response = await fetch(`${this.baseUrl}/sync/projects/batch-sync`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${this.apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        projectIds: [projectId]
      })
    })

    if (!response.ok) {
      const error = await response.text()
      throw new Error(`Failed to trigger sync: ${error}`)
    }
  }

  /**
   * Get branch comparison for a project
   */
  async getBranchComparison(syncProjectId: number): Promise<BranchComparison | null> {
    const response = await fetch(`${this.baseUrl}/sync/branches?syncProjectId=${syncProjectId}`, {
      headers: {
        'Authorization': `Bearer ${this.apiKey}`
      }
    })

    if (!response.ok) {
      throw new Error(`Failed to get branch comparison: ${response.statusText}`)
    }

    const result = await response.json()
    return result.success ? result.data : null
  }

  /**
   * Get sync result for a project
   */
  async getSyncResult(projectId: number): Promise<SyncResult | null> {
    const response = await fetch(`${this.baseUrl}/sync/projects/${projectId}/result`, {
      headers: {
        'Authorization': `Bearer ${this.apiKey}`
      }
    })

    if (response.status === 404) {
      return null
    }

    if (!response.ok) {
      throw new Error(`Failed to get sync result: ${response.statusText}`)
    }

    const result = await response.json()
    return result.success ? result.data : null
  }

  /**
   * Wait for sync to complete
   */
  async waitForSync(
    projectId: number,
    timeoutMs: number = 60000,
    checkIntervalMs: number = 2000
  ): Promise<SyncResult> {
    const startTime = Date.now()

    while (Date.now() - startTime < timeoutMs) {
      const result = await this.getSyncResult(projectId)

      if (result && result.syncStatus !== 'running') {
        return result
      }

      await new Promise(resolve => setTimeout(resolve, checkIntervalMs))
    }

    throw new Error(`Sync timeout after ${timeoutMs}ms`)
  }

  /**
   * Wait for branch to be synced
   */
  async waitForBranchSync(
    syncProjectId: number,
    branchName: string,
    expectedStatus: 'synced' | 'outdated' = 'synced',
    timeoutMs: number = 60000,
    checkIntervalMs: number = 2000
  ): Promise<BranchInfo> {
    const startTime = Date.now()

    while (Date.now() - startTime < timeoutMs) {
      const comparison = await this.getBranchComparison(syncProjectId)

      if (comparison) {
        const branch = comparison.branches.find(b => b.branchName === branchName)
        if (branch && branch.syncStatus === expectedStatus) {
          return branch
        }
      }

      await new Promise(resolve => setTimeout(resolve, checkIntervalMs))
    }

    throw new Error(`Branch sync timeout after ${timeoutMs}ms`)
  }
}

export const mirrorApi = new MirrorApiHelper()
