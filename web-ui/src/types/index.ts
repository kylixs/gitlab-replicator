// API Response wrapper
export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

// Dashboard
export interface DashboardStats {
  totalProjects: number
  syncedProjects: number
  syncingProjects: number
  pausedProjects: number
  failedProjects: number
}

export interface StatusDistribution {
  synced: number
  syncing: number
  pending: number
  paused: number
  failed: number
}

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
}

// Projects
export interface ProjectListItem {
  id: number
  projectKey: string
  syncStatus: string
  syncMethod: string
  lastSyncAt: string
  updatedAt: string
  groupPath?: string
  diff: {
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
    commitDiff: number
  }
  delay: {
    seconds: number
    formatted: string
  }
  nextSyncTime: string
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
  }
  details: Record<string, any>
}
