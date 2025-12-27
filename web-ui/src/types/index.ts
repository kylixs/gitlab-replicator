// API Response wrapper
export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

// Dashboard
export interface DashboardStats {
  totalProjects: number
  statusCounts: Record<string, number>  // Dynamic status counts
}

export interface StatusItem {
  status: string
  count: number
}

export type StatusDistribution = StatusItem[]

export interface DelayedProject {
  projectKey: string
  syncProjectId: number
  delaySeconds: number
  delayFormatted: string
  syncStatus: string
}

export interface RecentEvent {
  id: number
  syncProjectId: number
  projectKey: string
  eventType: string
  eventSource: string
  status: string
  eventTime: string
  durationSeconds: number
  statistics?: SyncStatistics
  errorMessage?: string
}

// Projects
export interface ProjectListItem {
  id: number
  projectKey: string
  syncStatus: string
  syncMethod: string
  lastSyncAt: string
  updatedAt: string
  lastSyncStatus?: string
  consecutiveFailures?: number
  lastSyncSummary?: string
  lastSyncErrorMessage?: string
  groupPath?: string
  taskStatus?: string
  lastCheckAt?: string
  lastCommitTime?: string
  diff: {
    diffStatus?: string
    branchNew: number
    branchDeleted: number
    branchOutdated: number
    commitDiff: number
  }
  delaySeconds: number
  delayFormatted: string
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

// Project Detail
export interface ProjectOverview {
  project: any
  source: any
  target: any
  diff: {
    branchNew: number
    branchDeleted: number
    branchOutdated: number
    branchAhead: number
    branchDiverged: number
    commitDiff: number
    diffStatus: string
  }
  delay: {
    seconds: number
    formatted: string
  }
  nextSyncTime: string
  cache?: {
    path?: string
    sizeBytes?: number
    sizeFormatted?: string
    lastModified?: string
    exists?: boolean
  }
  task?: {
    id: number
    taskType: string
    taskStatus: string
    nextRunAt: string
    lastRunAt: string
    startedAt?: string
    completedAt?: string
    lastSyncStatus: string
    durationSeconds: number
    consecutiveFailures: number
    errorMessage?: string
    lastSyncSummary?: string
    hasChanges?: boolean
    changesCount?: number
  }
}

// Branch Comparison
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
  syncStatus: 'synced' | 'outdated' | 'missing_in_target' | 'extra_in_target'
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
  delaySeconds?: number
  delayFormatted?: string
}

// Sync Statistics
export interface SyncStatistics {
  branchesCreated?: number
  branchesUpdated?: number
  branchesDeleted?: number
  commitsPushed?: number
  totalBranches?: number
  tagsSynced?: number
}

// Events
export interface EventListItem {
  id: number
  syncProjectId: number
  projectKey: string
  eventType: string
  status: string
  message: string
  durationMs: number
  createdAt: string
  statistics?: SyncStatistics
}

export interface EventStats {
  totalEvents: number
  successEvents: number
  failedEvents: number
  avgDurationMs: number
}

export interface EventDetails {
  event: {
    id: number
    syncProjectId: number
    projectKey: string
    eventType: string
    eventSource: string
    status: string
    eventTime: string
    durationSeconds: number
    statistics?: SyncStatistics
  }
  details: Record<string, any>
}

// Sync Results
export interface SyncResult {
  id: number
  syncProjectId: number
  projectKey: string
  syncStatus: string
  startedAt: string
  completedAt?: string
  durationSeconds?: number
  summary?: string
  statistics?: SyncStatistics
}

export interface SyncResultDetail extends SyncResult {
  totalBranches: number
  recentBranches: SyncResultBranchInfo[]
  syncMethod?: string
  hasChanges?: boolean
  changesCount?: number
  lastSyncAt?: string
  sourceCommitSha?: string
  errorMessage?: string
  statistics?: SyncStatistics
}

export interface SyncResultBranchInfo {
  branchName: string
  commitSha: string
  commitMessage: string
  commitAuthor: string
  committedAt: string
  isDefault: boolean
  isProtected: boolean
  isRecentlyUpdated: boolean  // 标记最近更新的分支（24小时内）
}

// Configuration
export interface SystemConfig {
  gitlab: GitLabConfig
  scanSettings: ScanSettings
  syncSettings: SyncSettings
  defaultSyncRules: DefaultSyncRules
  thresholds: Thresholds
}

export interface GitLabConfig {
  source: GitLabInstance
  target: GitLabInstance
}

export interface GitLabInstance {
  url: string
  token: string
}

export interface ScanSettings {
  incrementalInterval: number
  fullScanCron: string
  enabled: boolean
}

export interface SyncSettings {
  syncInterval: number
  concurrency: number
}

export interface DefaultSyncRules {
  method: string
  excludeArchived: boolean
  excludeEmpty: boolean
  excludePattern: string
}

export interface Thresholds {
  delayWarningHours: number
  delayCriticalHours: number
  maxRetryAttempts: number
  timeoutSeconds: number
}

export interface ConnectionTestResult {
  connected: boolean
  version?: string
  latencyMs?: number
  error?: string
}
