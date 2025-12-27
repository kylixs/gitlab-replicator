<template>
  <div class="sync-events">
    <!-- Event Statistics -->
    <EventStats :stats="stats" />

    <!-- Event Filter -->
    <EventFilter
      v-model="filters"
      @search="loadEvents"
      @export="handleExport"
    />

    <!-- Events Table -->
    <el-card class="table-card">
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

        <el-table-column prop="projectKey" label="Project" min-width="200">
          <template #default="{ row }">
            <div class="project-cell">
              <el-icon><FolderOpened /></el-icon>
              <a @click="handleViewProject(row)" class="project-link">
                {{ row.projectKey }}
              </a>
            </div>
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

        <el-table-column prop="message" label="Message" min-width="200">
          <template #default="{ row }">
            <div class="text-ellipsis-5">{{ row.message }}</div>
          </template>
        </el-table-column>

        <el-table-column label="Duration" width="120">
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

      <!-- Pagination -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadEvents"
          @current-change="loadEvents"
        />
      </div>
    </el-card>

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

        <!-- Error Message for Failed Events -->
        <div v-if="eventDetailEnhanced.errorMessage" class="error-section">
          <el-divider />
          <h3>Error Details</h3>
          <el-alert type="error" :closable="false">
            {{ eventDetailEnhanced.errorMessage }}
          </el-alert>
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EventStats from '@/components/events/EventStats.vue'
import EventFilter from '@/components/events/EventFilter.vue'
import { eventsApi, type EventDetailEnhanced } from '@/api/events'
import { useAutoRefreshTimer } from '@/composables/useAutoRefresh'
import type { EventListItem, EventStats as EventStatsType, EventDetails } from '@/types'
import { Refresh, Warning, Document } from '@element-plus/icons-vue'

const router = useRouter()

const loading = ref(false)
const events = ref<EventListItem[]>([])
const stats = ref<EventStatsType | null>(null)
const selectedEvent = ref<EventListItem | null>(null)
const eventDetails = ref<EventDetails | null>(null)
const eventDetailEnhanced = ref<EventDetailEnhanced | null>(null)
const detailDrawerVisible = ref(false)

const filters = reactive({
  eventType: '',
  status: '',
  startDate: '',
  endDate: '',
  search: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loadEvents = async () => {
  loading.value = true
  try {
    const params = {
      ...filters,
      page: pagination.page,
      size: pagination.size
    }

    const response = await eventsApi.getEvents(params)
    events.value = response.data.items
    pagination.total = response.data.total
  } catch (error) {
    ElMessage.error('Failed to load events')
    console.error('Load events failed:', error)
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const today = new Date().toISOString().split('T')[0]
    const response = await eventsApi.getEventStats(today)
    stats.value = response.data
  } catch (error) {
    console.error('Load stats failed:', error)
  }
}

const handleViewProject = (event: EventListItem) => {
  if (event.syncProjectId) {
    router.push(`/projects/${event.syncProjectId}`)
  }
}

const handleViewDetail = async (event: EventListItem) => {
  selectedEvent.value = event
  eventDetails.value = null
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

// Refresh both stats and events
const refreshData = () => {
  loadStats()
  loadEvents()
}

// Use auto-refresh timer
const { startTimer, stopTimer } = useAutoRefreshTimer(refreshData)

const handleExport = () => {
  try {
    // CSV headers
    const headers = [
      'Time',
      'Project',
      'Event Type',
      'Status',
      'Message',
      'Duration'
    ]

    // Convert events data to CSV rows
    const rows = events.value.map(event => [
      formatTime(event.createdAt),
      event.projectKey,
      formatEventType(event.eventType),
      event.status,
      event.message || '',
      event.durationMs ? formatDuration(event.durationMs) : '-'
    ])

    // Create CSV content
    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.map(cell => `"${cell}"`).join(','))
    ].join('\n')

    // Create blob and download
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)

    link.setAttribute('href', url)
    link.setAttribute('download', `sync_events_${new Date().toISOString().split('T')[0]}.csv`)
    link.style.visibility = 'hidden'

    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)

    ElMessage.success(`Exported ${events.value.length} events`)
  } catch (error) {
    ElMessage.error('Failed to export events')
    console.error('Export failed:', error)
  }
}

onMounted(() => {
  loadStats()
  loadEvents()
  startTimer()
})

onUnmounted(() => {
  stopTimer()
})
</script>

<style scoped>
.sync-events {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card {
  border-radius: 8px;
}

.project-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-link {
  color: #1890ff;
  cursor: pointer;
  font-weight: 500;
}

.project-link:hover {
  text-decoration: underline;
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

.event-detail {
  padding: 0 8px;
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

.error-section {
  margin-top: 16px;
}

.branch-summary {
  margin: 16px 0;
}

.text-ellipsis-5 {
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.5;
  max-height: 7.5em; /* 5 lines * 1.5 line-height */
  word-break: break-word;
}

</style>
