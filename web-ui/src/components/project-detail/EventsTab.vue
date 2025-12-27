<template>
  <div class="events-tab">
    <el-table
      v-loading="loading"
      :data="events"
      stripe
      style="width: 100%"
    >
      <el-table-column prop="createdAt" label="Time" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="Event Type" width="160">
        <template #default="{ row }">
          <div class="event-type-cell">
            <el-icon :size="16"><component :is="getEventIcon(row.eventType)" /></el-icon>
            <span>{{ formatEventType(row.eventType) }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="Status" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="message" label="Message" min-width="450">
        <template #default="{ row }">
          <div class="message-container">
            <el-icon v-if="isSkippedSync(row.message)" :size="16" class="skip-icon"><CircleCheck /></el-icon>
            <div :class="['message-text', { 'skip-message': isSkippedSync(row.message) }]">
              {{ formatCompactMessage(row) }}
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="Duration" width="100">
        <template #default="{ row }">
          <span v-if="row.durationMs">{{ formatDuration(row.durationMs) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column label="Actions" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleViewDetail(row)">
            Detail
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadEvents"
        @current-change="loadEvents"
      />
    </div>

    <!-- Event Detail Drawer -->
    <el-drawer
      v-model="detailDrawerVisible"
      title="Event Detail"
      size="60%"
      direction="rtl"
    >
      <div v-if="selectedEvent && eventDetailEnhanced" class="event-detail">
        <!-- Basic Info -->
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Event ID">
            {{ eventDetailEnhanced.id }}
          </el-descriptions-item>
          <el-descriptions-item label="Project">
            {{ eventDetailEnhanced.projectKey }}
          </el-descriptions-item>
          <el-descriptions-item label="Event Type">
            {{ formatEventType(eventDetailEnhanced.eventType) }}
          </el-descriptions-item>
          <el-descriptions-item label="Status">
            <el-tag :type="getStatusType(eventDetailEnhanced.status)">
              {{ eventDetailEnhanced.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Time">
            {{ formatTime(eventDetailEnhanced.eventTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="Duration" v-if="eventDetailEnhanced.durationSeconds">
            {{ formatDuration(eventDetailEnhanced.durationSeconds * 1000) }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- Sync Summary Table -->
        <div v-if="eventDetailEnhanced.statistics && hasStatistics(eventDetailEnhanced.statistics)">
          <el-divider />
          <h3>Sync Summary</h3>
          <el-table :data="[eventDetailEnhanced.statistics]" border style="width: 100%">
            <el-table-column label="Branches Created" width="150" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.branchesCreated && row.branchesCreated > 0" type="success" size="large">
                  +{{ row.branchesCreated }}
                </el-tag>
                <span v-else style="color: #909399">0</span>
              </template>
            </el-table-column>
            <el-table-column label="Branches Updated" width="150" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.branchesUpdated && row.branchesUpdated > 0" type="primary" size="large">
                  ~{{ row.branchesUpdated }}
                </el-tag>
                <span v-else style="color: #909399">0</span>
              </template>
            </el-table-column>
            <el-table-column label="Branches Deleted" width="150" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.branchesDeleted && row.branchesDeleted > 0" type="danger" size="large">
                  -{{ row.branchesDeleted }}
                </el-tag>
                <span v-else style="color: #909399">0</span>
              </template>
            </el-table-column>
            <el-table-column label="Commits Pushed" width="150" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.commitsPushed && row.commitsPushed > 0" type="info" size="large">
                  {{ row.commitsPushed }}
                </el-tag>
                <span v-else style="color: #909399">0</span>
              </template>
            </el-table-column>
            <el-table-column label="Message" min-width="200">
              <template #default>
                <span v-if="eventDetailEnhanced.eventData?.message">{{ eventDetailEnhanced.eventData.message }}</span>
                <span v-else style="color: #909399">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- Error Message for Failed Events -->
        <div v-if="eventDetailEnhanced.errorMessage" class="error-section">
          <el-divider />
          <h3>Error Details</h3>
          <el-alert type="error" :closable="false">
            <pre class="message-content">{{ eventDetailEnhanced.errorMessage }}</pre>
          </el-alert>
        </div>

        <!-- Changed Branches Details -->
        <div v-if="eventDetailEnhanced.statistics?.changedBranches && eventDetailEnhanced.statistics.changedBranches.length > 0">
          <el-divider />
          <h3>Changed Branches ({{ eventDetailEnhanced.statistics.changedBranches.length }})</h3>
          <el-table :data="eventDetailEnhanced.statistics.changedBranches" stripe style="width: 100%">
            <el-table-column label="Branch" min-width="150">
              <template #default="{ row }">
                <span style="font-weight: 500">{{ row.branchName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Change Type" width="120">
              <template #default="{ row }">
                <el-tag
                  :type="row.changeType === 'created' ? 'success' : row.changeType === 'updated' ? 'primary' : 'danger'"
                  size="small"
                >
                  {{ row.changeType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Commit" width="100">
              <template #default="{ row }">
                <el-tooltip :content="row.commitSha">
                  <code style="font-size: 12px">{{ row.commitSha ? row.commitSha.substring(0, 8) : '-' }}</code>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="commitTitle" label="Commit Message" min-width="200" />
            <el-table-column prop="commitAuthor" label="Author" width="120" />
            <el-table-column label="Committed At" width="180">
              <template #default="{ row }">
                {{ row.commitTime || '-' }}
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- Branch Information for Successful Events -->
        <div v-if="eventDetailEnhanced.totalBranches !== undefined && eventDetailEnhanced.totalBranches > 0">
          <el-divider />
          <h3>Sync Summary</h3>
          <div class="branch-summary">
            <el-tag type="info" size="large">
              Total Branches: {{ eventDetailEnhanced.totalBranches }}
            </el-tag>
          </div>

          <h4 style="margin-top: 20px">Recent 10 Branches</h4>
          <el-table :data="eventDetailEnhanced.recentBranches" stripe style="width: 100%">
            <el-table-column label="Branch" min-width="150">
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

        <!-- Event Data (JSON) -->
        <div v-if="eventDetailEnhanced.eventData">
          <el-divider />
          <h3>Event Data</h3>
          <div class="event-details-json">
            <pre>{{ JSON.stringify(eventDetailEnhanced.eventData, null, 2) }}</pre>
          </div>
        </div>
      </div>
      <div v-else class="loading-details">
        <el-icon class="is-loading"><Loading /></el-icon>
        Loading details...
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { eventsApi, type EventDetailEnhanced } from '@/api/events'
import type { EventListItem } from '@/types'
import { Refresh, Warning, Document, CircleCheck, Loading } from '@element-plus/icons-vue'

interface Props {
  projectId: number
  projectKey: string
}

const props = defineProps<Props>()

const loading = ref(false)
const events = ref<EventListItem[]>([])
const detailDrawerVisible = ref(false)
const selectedEvent = ref<EventListItem | null>(null)
const eventDetailEnhanced = ref<EventDetailEnhanced | null>(null)

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loadEvents = async () => {
  loading.value = true
  try {
    const response = await eventsApi.getEvents({
      search: props.projectKey,
      page: pagination.page,
      size: pagination.size
    })
    events.value = response.data.items.filter(
      event => event.projectKey === props.projectKey
    )
    pagination.total = events.value.length
  } catch (error) {
    ElMessage.error('Failed to load events')
    console.error('Load events failed:', error)
  } finally {
    loading.value = false
  }
}

const getEventIcon = (eventType: string) => {
  const iconMap: Record<string, any> = {
    'incremental_sync': Refresh,
    'full_sync': Refresh,
    'manual_sync': Refresh,
    'webhook_sync': Refresh,
    'status_change': Warning,
    'discovery': Document
  }
  return iconMap[eventType] || Document
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'success': 'success',
    'running': 'info',
    'failed': 'danger'
  }
  return typeMap[status.toLowerCase()] || 'info'
}

const formatEventType = (eventType: string) => {
  return eventType.split('_').map(word =>
    word.charAt(0).toUpperCase() + word.slice(1)
  ).join(' ')
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

const formatDuration = (ms: number) => {
  if (!ms) return '0s'
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}m ${remainingSeconds}s`
}

const isSkippedSync = (message: string) => {
  if (!message) return false
  const skipPatterns = [
    'skipped',
    '跳过',
    'no changes',
    '无变更',
    'no branch changes'
  ]
  const lowerMessage = message.toLowerCase()
  return skipPatterns.some(pattern => lowerMessage.includes(pattern))
}

const hasStatistics = (statistics: any) => {
  if (!statistics) return false
  return (statistics.branchesCreated && statistics.branchesCreated > 0) ||
         (statistics.branchesUpdated && statistics.branchesUpdated > 0) ||
         (statistics.branchesDeleted && statistics.branchesDeleted > 0) ||
         (statistics.commitsPushed && statistics.commitsPushed > 0)
}

const formatCompactMessage = (event: EventListItem) => {
  const stats = event.statistics

  // If no statistics, return original message
  if (!stats || !hasStatistics(stats)) {
    return event.message || '-'
  }

  // Build multi-line compact message with readable first line
  const parts: string[] = []
  if (stats.branchesCreated) parts.push(`+${stats.branchesCreated} ${stats.branchesCreated === 1 ? 'branch' : 'branches'}`)
  if (stats.branchesUpdated) parts.push(`~${stats.branchesUpdated} ${stats.branchesUpdated === 1 ? 'branch' : 'branches'}`)
  if (stats.branchesDeleted) parts.push(`-${stats.branchesDeleted} ${stats.branchesDeleted === 1 ? 'branch' : 'branches'}`)
  if (stats.commitsPushed) parts.push(`${stats.commitsPushed} ${stats.commitsPushed === 1 ? 'commit' : 'commits'}`)

  let message = parts.join('  ')

  // Add top 3 changed branches (one per line)
  if (stats.changedBranches && stats.changedBranches.length > 0) {
    const limit = Math.min(3, stats.changedBranches.length)
    for (let i = 0; i < limit; i++) {
      const branch = stats.changedBranches[i]
      const shortSha = branch.commitSha ? branch.commitSha.substring(0, 8) : ''
      message += `\n${branch.branchName} | ${shortSha}`
    }

    // Add "+N more" if there are more branches
    const remaining = stats.changedBranches.length - limit
    if (remaining > 0) {
      message += `\n+${remaining} more...`
    }
  }

  return message
}

const handleViewDetail = async (event: EventListItem) => {
  selectedEvent.value = event
  eventDetailEnhanced.value = null
  detailDrawerVisible.value = true

  try {
    const response = await eventsApi.getEventDetail(event.id)
    eventDetailEnhanced.value = response.data
  } catch (error) {
    ElMessage.error('Failed to load event details')
    console.error('Load event details failed:', error)
  }
}

onMounted(() => {
  loadEvents()
})
</script>

<style scoped>
.events-tab {
  padding: 0;
}

.event-type-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.message-container {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  max-height: 7.5em; /* ~5 lines */
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.skip-icon {
  color: #67c23a;
  flex-shrink: 0;
  margin-top: 2px;
}

.skip-message {
  color: #67c23a;
  font-style: italic;
}

.event-detail {
  padding: 0 8px;
}

.error-section,
.success-section {
  margin-top: 16px;
}

.message-content {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.branch-summary {
  margin: 16px 0;
}

.statistics-summary {
  margin: 16px 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.event-details-json {
  background-color: #f5f5f5;
  border-radius: 4px;
  padding: 16px;
  overflow-x: auto;
}

.event-details-json pre {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #333;
}

.loading-details {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
  padding: 16px;
}
</style>
