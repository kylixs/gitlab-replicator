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

      <el-table-column prop="message" label="Message" min-width="200">
        <template #default="{ row }">
          <div class="message-cell">
            <el-icon v-if="isSkippedSync(row.message)" :size="16" class="skip-icon"><CircleCheck /></el-icon>
            <span :class="{ 'skip-message': isSkippedSync(row.message) }">{{ row.message }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="Duration" width="120">
        <template #default="{ row }">
          <span v-if="row.durationMs">{{ formatDuration(row.durationMs) }}</span>
          <span v-else>-</span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { eventsApi } from '@/api/events'
import type { EventListItem } from '@/types'
import { Refresh, Warning, Document, CircleCheck } from '@element-plus/icons-vue'

interface Props {
  projectId: number
  projectKey: string
}

const props = defineProps<Props>()

const loading = ref(false)
const events = ref<EventListItem[]>([])

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

.message-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.skip-icon {
  color: #67c23a;
}

.skip-message {
  color: #67c23a;
  font-style: italic;
}
</style>
