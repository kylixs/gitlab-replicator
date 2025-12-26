<template>
  <div class="overview-tab">
    <!-- Status Cards -->
    <el-row :gutter="16" class="status-cards">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon success">
              <el-icon :size="24"><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Status</div>
              <el-tag :type="getStatusType(overview.project.syncStatus)">
                {{ overview.project.syncStatus }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon warning">
              <el-icon :size="24"><Timer /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Delay</div>
              <div class="stat-value">{{ overview.delay?.formatted || '-' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon info">
              <el-icon :size="24"><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Last Sync</div>
              <div class="stat-value">{{ formatTime(overview.project.lastSyncAt) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon primary">
              <el-icon :size="24"><Calendar /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Next Sync</div>
              <div class="stat-value">{{ formatTime(overview.nextSyncTime) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Diff Status Badge -->
    <el-card v-if="overview.diff?.diffStatus" shadow="hover" class="diff-status-card">
      <div class="diff-status-container">
        <div class="diff-status-label">Overall Diff Status</div>
        <el-tag :type="getDiffStatusType(overview.diff.diffStatus)" size="large" class="diff-status-badge">
          <el-icon class="status-icon">
            <component :is="getDiffStatusIcon(overview.diff.diffStatus)" />
          </el-icon>
          {{ getDiffStatusLabel(overview.diff.diffStatus) }}
        </el-tag>
      </div>
    </el-card>

    <!-- Diff Statistics -->
    <el-row :gutter="16" class="diff-stats">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">New Branches</div>
            <div class="diff-value new">+{{ overview.diff?.branchNew || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Deleted Branches</div>
            <div class="diff-value deleted">-{{ overview.diff?.branchDeleted || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Outdated Branches</div>
            <div class="diff-value outdated">~{{ overview.diff?.branchOutdated || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Ahead Branches</div>
            <div class="diff-value ahead">↑{{ overview.diff?.branchAhead || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Diverged Branches</div>
            <div class="diff-value diverged">⚡{{ overview.diff?.branchDiverged || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Commit Diff</div>
            <div class="diff-value new">+{{ overview.diff?.commitDiff || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Project Information -->
    <el-row :gutter="16" class="project-info-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>Source Project</span>
              <el-tag type="info" size="small">GitLab</el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Project ID">
              {{ overview.source?.id }}
            </el-descriptions-item>
            <el-descriptions-item label="Name">
              {{ overview.source?.name }}
            </el-descriptions-item>
            <el-descriptions-item label="Path">
              {{ overview.source?.pathWithNamespace }}
            </el-descriptions-item>
            <el-descriptions-item label="Default Branch">
              {{ overview.source?.defaultBranch }}
            </el-descriptions-item>
            <el-descriptions-item label="Last Activity">
              {{ formatTime(overview.source?.lastActivityAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="Repository Size">
              {{ formatSize(overview.source?.repositorySize) }}
            </el-descriptions-item>
            <el-descriptions-item label="Commit Count">
              {{ overview.source?.commitCount || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>Target Project</span>
              <el-tag type="success" size="small">GitLab</el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Project ID">
              {{ overview.target?.id }}
            </el-descriptions-item>
            <el-descriptions-item label="Name">
              {{ overview.target?.name }}
            </el-descriptions-item>
            <el-descriptions-item label="Path">
              {{ overview.target?.pathWithNamespace }}
            </el-descriptions-item>
            <el-descriptions-item label="Default Branch">
              {{ overview.target?.defaultBranch }}
            </el-descriptions-item>
            <el-descriptions-item label="Last Activity">
              {{ formatTime(overview.target?.lastActivityAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="Repository Size">
              {{ formatSize(overview.target?.repositorySize) }}
            </el-descriptions-item>
            <el-descriptions-item label="Commit Count">
              {{ overview.target?.commitCount || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- Sync Configuration -->
    <el-card shadow="hover" class="sync-config">
      <template #header>
        <span>Sync Configuration</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Sync Method">
          <el-tag type="info">
            {{ overview.project.syncMethod === 'push_mirror' ? 'Push Mirror' : 'Pull Sync' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Created At">
          {{ formatTime(overview.project.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Last Commit Time">
          {{ formatTime(overview.source?.lastActivityAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Last Sync At">
          {{ formatTime(overview.project.lastSyncAt) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- Sync Task Information -->
    <el-card v-if="overview.task" shadow="hover" class="task-info">
      <template #header>
        <div class="card-header">
          <span>Sync Task Information</span>
          <div class="header-actions">
            <el-tag :type="getTaskStatusType(overview.task.taskStatus)" size="small">
              {{ overview.task.taskStatus }}
            </el-tag>
            <el-button-group size="small" style="margin-left: 8px">
              <el-button @click="handleViewTaskDetail">Detail</el-button>
              <el-button @click="handleViewTaskLogs">Logs</el-button>
            </el-button-group>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="Task Type">
          <el-tag type="info">{{ overview.task.taskType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Current Status">
          <el-tag :type="getTaskStatusType(overview.task.taskStatus)">
            {{ formatTaskStatus(overview.task.taskStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Last Sync Status">
          <el-tag v-if="overview.task.lastSyncStatus" :type="overview.task.lastSyncStatus === 'success' ? 'success' : (overview.task.lastSyncStatus === 'skipped' ? 'info' : 'danger')">
            {{ overview.task.lastSyncStatus }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="Last Sync Result" v-if="overview.task.lastSyncStatus === 'success' || overview.task.lastSyncStatus === 'skipped'">
          <span v-if="overview.task.hasChanges !== null && overview.task.hasChanges !== undefined">
            <el-tag v-if="overview.task.hasChanges" type="success">
              {{ overview.task.changesCount || 0 }} changes synced
            </el-tag>
            <el-tag v-else type="info">
              No changes (skipped)
            </el-tag>
          </span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="Started At">
          {{ formatInstant(overview.task.startedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Last Run At">
          {{ formatInstant(overview.task.lastRunAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Completed At">
          {{ formatInstant(overview.task.completedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Next Run At">
          {{ formatInstant(overview.task.nextRunAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Duration">
          <span v-if="overview.task.durationSeconds">{{ overview.task.durationSeconds }} seconds</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="Consecutive Failures">
          <el-tag v-if="overview.task.consecutiveFailures > 0" type="danger">
            {{ overview.task.consecutiveFailures }}
          </el-tag>
          <span v-else>0</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="overview.task.errorMessage" label="Error Message" :span="2">
          <el-text type="danger">{{ overview.task.errorMessage }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- Cache Information -->
    <el-card v-if="overview.cache" shadow="hover" class="cache-info">
      <template #header>
        <div class="card-header">
          <span>Git Cache Information</span>
          <el-tag :type="overview.cache.exists ? 'success' : 'info'" size="small">
            {{ overview.cache.exists ? 'Exists' : 'Not Found' }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Cache Path" :span="2">
          <code>{{ overview.cache.path || '-' }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="Cache Size">
          {{ overview.cache.sizeFormatted || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="Last Modified">
          {{ formatTime(overview.cache.lastModified) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- Sync Result Detail Dialog -->
    <el-dialog
      v-model="detailDialogVisible"
      title="Sync Result Detail"
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-loading="detailLoading">
        <div v-if="resultDetail">
          <!-- Basic Information -->
          <el-descriptions :column="2" border>
            <el-descriptions-item label="Project">
              {{ resultDetail.projectKey }}
            </el-descriptions-item>
            <el-descriptions-item label="Sync Method">
              {{ resultDetail.syncMethod }}
            </el-descriptions-item>
            <el-descriptions-item label="Status">
              <el-tag :type="resultDetail.syncStatus === 'success' ? 'success' : 'danger'">
                {{ resultDetail.syncStatus }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Changes">
              <el-tag v-if="resultDetail.hasChanges" type="warning">
                {{ resultDetail.changesCount }} changes
              </el-tag>
              <el-tag v-else type="info">No changes</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Duration">
              {{ formatDuration(resultDetail.durationSeconds) }}
            </el-descriptions-item>
            <el-descriptions-item label="Last Sync">
              {{ formatTime(resultDetail.lastSyncAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="Source Commit" :span="2">
              <code v-if="resultDetail.sourceCommitSha">{{ resultDetail.sourceCommitSha }}</code>
              <span v-else>-</span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- Summary -->
          <el-divider />
          <h3>Summary</h3>
          <div v-if="resultDetail.errorMessage" style="color: #f56c6c;">
            {{ resultDetail.errorMessage }}
          </div>
          <div v-else>{{ resultDetail.summary || 'No summary available' }}</div>

          <!-- Branch Information -->
          <el-divider />
          <h3>Branch Information</h3>
          <div style="margin: 16px 0;">
            <el-tag type="info" size="large">
              Total Branches: {{ resultDetail.totalBranches }}
            </el-tag>
          </div>

          <h4 style="margin-top: 20px">Recent 10 Branches</h4>
          <el-table :data="resultDetail.recentBranches" stripe style="width: 100%">
            <el-table-column prop="branchName" label="Branch" min-width="150">
              <template #default="{ row }">
                <span style="font-weight: 500">{{ row.branchName }}</span>
                <el-tag v-if="row.isDefault" type="success" size="small" style="margin-left: 8px">
                  Default
                </el-tag>
                <el-tag v-if="row.isProtected" type="warning" size="small" style="margin-left: 8px">
                  Protected
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Commit" width="200">
              <template #default="{ row }">
                <el-tooltip :content="row.commitSha">
                  <code style="font-size: 12px">{{ row.commitSha.substring(0, 8) }}</code>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="commitMessage" label="Message" min-width="250" />
            <el-table-column prop="commitAuthor" label="Author" width="150" />
            <el-table-column label="Committed At" width="180">
              <template #default="{ row }">
                {{ formatTime(row.committedAt) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- Sync Logs Dialog -->
    <el-dialog
      v-model="logsDialogVisible"
      title="Sync Logs"
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-loading="logsLoading" style="max-height: 600px; overflow-y: auto;">
        <el-empty v-if="!logsLoading && syncLogs.length === 0" description="No sync events found" />

        <el-timeline v-else>
          <el-timeline-item
            v-for="log in syncLogs"
            :key="log.id"
            :timestamp="formatTime(log.createdAt)"
            :type="getLogType(log.status)"
            placement="top"
          >
            <el-card>
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span style="font-weight: bold;">{{ formatEventType(log.eventType) }}</span>
                  <el-tag :type="getStatusTagType(log.status)" size="small">
                    {{ log.status }}
                  </el-tag>
                </div>
              </template>
              <div style="margin: 8px 0; color: #606266; font-size: 14px; line-height: 1.6;">
                {{ log.message }}
              </div>
              <div v-if="log.durationMs" style="margin-top: 8px; color: #909399; font-size: 12px;">
                Duration: {{ formatDuration(log.durationMs / 1000) }}
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { SuccessFilled, WarningFilled, CircleCloseFilled, QuestionFilled } from '@element-plus/icons-vue'
import { syncApi } from '@/api/sync'
import type { ProjectOverview } from '@/types'

interface Props {
  overview: ProjectOverview
}

const props = defineProps<Props>()
const emit = defineEmits(['refresh'])

import { ref } from 'vue'
import { eventsApi } from '@/api/events'
import type { SyncResultDetail, EventListItem } from '@/types'

const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const resultDetail = ref<SyncResultDetail | null>(null)

const logsDialogVisible = ref(false)
const logsLoading = ref(false)
const syncLogs = ref<EventListItem[]>([])

const handleViewTaskDetail = async () => {
  const task = props.overview.task
  if (!task) return

  // Find latest sync result for this project
  try {
    detailDialogVisible.value = true
    detailLoading.value = true
    resultDetail.value = null

    const response = await syncApi.getSyncResults({
      search: props.overview.project.projectKey,
      page: 1,
      size: 1
    })

    if (response.data && response.data.items.length > 0) {
      const latestResult = response.data.items[0]
      if (latestResult) {
        // Load detail
        const detailResponse = await syncApi.getSyncResultDetail(latestResult.id)
        if (detailResponse.data) {
          resultDetail.value = detailResponse.data
        }
      }
    } else {
      ElMessage.info('No sync results found for this project')
      detailDialogVisible.value = false
    }
  } catch (error) {
    ElMessage.error('Failed to load sync result')
    console.error('Load sync result failed:', error)
  } finally {
    detailLoading.value = false
  }
}

const handleViewTaskLogs = async () => {
  const task = props.overview.task
  if (!task) return

  try {
    logsDialogVisible.value = true
    logsLoading.value = true
    syncLogs.value = []

    // Find latest sync result to get time range
    const response = await syncApi.getSyncResults({
      search: props.overview.project.projectKey,
      page: 1,
      size: 1
    })

    if (response.data && response.data.items.length > 0) {
      const latestResult = response.data.items[0]
      const startDate = latestResult?.startedAt ? new Date(latestResult.startedAt).toISOString().split('T')[0] : undefined
      const endDate = latestResult?.completedAt ? new Date(latestResult.completedAt).toISOString().split('T')[0] : undefined

      const eventsResponse = await eventsApi.getEvents({
        search: props.overview.project.projectKey,
        startDate: startDate,
        endDate: endDate,
        page: 1,
        size: 50
      })

      if (eventsResponse.data) {
        // Filter by exact time range
        const startTime = latestResult?.startedAt ? new Date(latestResult.startedAt).getTime() : 0
        const endTime = latestResult?.completedAt ? new Date(latestResult.completedAt).getTime() : Date.now()

        syncLogs.value = (eventsResponse.data.items || []).filter((log: EventListItem) => {
          const logTime = new Date(log.createdAt).getTime()
          return logTime >= startTime && logTime <= endTime
        })
      }
    } else {
      ElMessage.info('No sync logs found for this project')
      logsDialogVisible.value = false
    }
  } catch (error) {
    console.error('Failed to load sync logs:', error)
    ElMessage.error('Failed to load sync logs')
  } finally {
    logsLoading.value = false
  }
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'synced': 'success',
    'syncing': 'info',
    'outdated': 'warning',
    'paused': 'info',
    'failed': 'danger',
    'pending': 'warning'
  }
  return typeMap[status.toLowerCase()] || 'info'
}

const getDiffStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'SYNCED': 'success',
    'OUTDATED': 'warning',
    'AHEAD': 'warning',
    'DIVERGED': 'danger',
    'INCONSISTENT': 'danger',
    'PENDING': 'info',
    'MISSING': 'danger'
  }
  return typeMap[status] || 'info'
}

const getDiffStatusLabel = (status: string) => {
  const labelMap: Record<string, string> = {
    'SYNCED': 'Synced',
    'OUTDATED': 'Outdated',
    'AHEAD': 'Ahead',
    'DIVERGED': 'Diverged',
    'INCONSISTENT': 'Inconsistent',
    'PENDING': 'Pending',
    'MISSING': 'Missing'
  }
  return labelMap[status] || status
}

const getDiffStatusIcon = (status: string) => {
  const iconMap: Record<string, any> = {
    'SYNCED': SuccessFilled,
    'OUTDATED': WarningFilled,
    'AHEAD': WarningFilled,
    'DIVERGED': CircleCloseFilled,
    'INCONSISTENT': CircleCloseFilled,
    'PENDING': QuestionFilled,
    'MISSING': CircleCloseFilled
  }
  return iconMap[status] || QuestionFilled
}

const formatTime = (time: string | null | undefined) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

const formatInstant = (instant: string | null | undefined) => {
  if (!instant) return '-'
  return new Date(instant).toLocaleString()
}

const formatSize = (bytes: number | null | undefined) => {
  if (!bytes || bytes === 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`
}

const formatDuration = (seconds: number | null | undefined) => {
  if (!seconds || seconds === 0) return '-'
  if (seconds < 60) return `${seconds}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
  const hours = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  return `${hours}h ${mins}m ${secs}s`
}

const getLogType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'success': 'success',
    'info': 'info',
    'warning': 'warning',
    'error': 'danger',
    'failed': 'danger'
  }
  return typeMap[status?.toLowerCase()] || 'info'
}

const formatEventType = (type: string) => {
  return type.replace(/_/g, ' ').toUpperCase()
}

const getStatusTagType = (status: string) => {
  return getStatusType(status)
}

const getTaskStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'waiting': 'info',
    'pending': 'warning',
    'running': 'success'
  }
  return typeMap[status.toLowerCase()] || 'info'
}

const formatTaskStatus = (status: string) => {
  const statusMap: Record<string, string> = {
    'waiting': 'Waiting',
    'pending': 'Pending',
    'running': 'Running'
  }
  return statusMap[status.toLowerCase()] || status
}
</script>

<style scoped>
.overview-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-cards,
.diff-stats,
.project-info-row {
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 8px;
}

.stat-icon.success {
  background-color: #f0f9ff;
  color: #52c41a;
}

.stat-icon.warning {
  background-color: #fffbe6;
  color: #faad14;
}

.stat-icon.info {
  background-color: #e6f4ff;
  color: #1890ff;
}

.stat-icon.primary {
  background-color: #f0f5ff;
  color: #2f54eb;
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.diff-card {
  text-align: center;
}

.diff-item {
  padding: 8px 0;
}

.diff-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.diff-value {
  font-size: 32px;
  font-weight: bold;
}

.diff-value.new {
  color: #52c41a;
}

.diff-value.deleted {
  color: #ff4d4f;
}

.diff-value.outdated {
  color: #faad14;
}

.diff-value.ahead {
  color: #fa8c16;
}

.diff-value.diverged {
  color: #f5222d;
}

.diff-status-card {
  margin-bottom: 16px;
}

.diff-status-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.diff-status-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.diff-status-badge {
  font-size: 16px;
  padding: 8px 16px;
}

.status-icon {
  margin-right: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sync-config {
  margin-bottom: 0;
}

.sync-summary-banner {
  margin-bottom: 16px;
  font-size: 14px;
}
</style>
