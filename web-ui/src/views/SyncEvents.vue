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

        <el-table-column prop="message" label="Message" min-width="200" />

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
      size="50%"
      direction="rtl"
    >
      <div v-if="selectedEvent" class="event-detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Event ID">
            {{ selectedEvent.id }}
          </el-descriptions-item>
          <el-descriptions-item label="Project">
            {{ selectedEvent.projectKey }}
          </el-descriptions-item>
          <el-descriptions-item label="Event Type">
            {{ formatEventType(selectedEvent.eventType) }}
          </el-descriptions-item>
          <el-descriptions-item label="Status">
            <el-tag :type="getStatusType(selectedEvent.status)">
              {{ selectedEvent.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Time">
            {{ formatTime(selectedEvent.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="Duration" v-if="selectedEvent.durationMs">
            {{ formatDuration(selectedEvent.durationMs) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h3>Event Details</h3>
        <div v-if="eventDetails" class="event-details-json">
          <pre>{{ JSON.stringify(eventDetails, null, 2) }}</pre>
        </div>
        <div v-else class="loading-details">
          <el-icon class="is-loading"><Loading /></el-icon>
          Loading details...
        </div>
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
import { eventsApi } from '@/api/events'
import type { EventListItem, EventStats as EventStatsType, EventDetails } from '@/types'
import { Refresh, Warning, Document } from '@element-plus/icons-vue'

const router = useRouter()

const loading = ref(false)
const events = ref<EventListItem[]>([])
const stats = ref<EventStatsType | null>(null)
const selectedEvent = ref<EventListItem | null>(null)
const eventDetails = ref<EventDetails | null>(null)
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

let refreshTimer: number | null = null

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
  detailDrawerVisible.value = true

  try {
    const response = await eventsApi.getEventDetails(event.id)
    eventDetails.value = response.data
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

const startAutoRefresh = () => {
  refreshTimer = window.setInterval(() => {
    loadStats()
    loadEvents()
  }, 30000)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

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
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
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
}

.loading-details {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
  padding: 16px;
}
</style>
