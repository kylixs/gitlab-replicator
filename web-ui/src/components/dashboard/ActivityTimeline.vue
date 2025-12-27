<template>
  <el-card class="activity-timeline">
    <template #header>
      <div class="timeline-header">
        <span>Recent Activities</span>
        <div class="filters">
          <el-select
            v-model="selectedType"
            placeholder="All Types"
            size="small"
            style="width: 180px"
            clearable
          >
            <el-option label="All Types" value="" />
            <el-option label="Sync Finished" value="sync_finished" />
            <el-option label="Sync Failed" value="sync_failed" />
            <el-option label="Task Blocked" value="task_blocked" />
            <el-option label="Target Created" value="target_project_created" />
          </el-select>
          <el-select
            v-model="selectedStatus"
            placeholder="All Status"
            size="small"
            style="width: 140px"
            clearable
          >
            <el-option label="All Status" value="" />
            <el-option label="Success" value="success" />
            <el-option label="Failed" value="failed" />
          </el-select>
        </div>
      </div>
    </template>
    <div v-if="sortedEvents && sortedEvents.length > 0" class="event-list">
      <div
        v-for="event in sortedEvents"
        :key="event.id"
        class="event-item"
        :class="{ expanded: expandedEvents.has(event.id) }"
        @click="toggleExpand(event.id)"
      >
        <div class="event-summary">
          <div class="event-left">
            <el-icon :size="16" :color="getEventColor(event.status)">
              <component :is="getEventIcon(event.eventType)" />
            </el-icon>
            <span class="event-time">{{ formatTime(event.eventTime) }}</span>
            <span class="event-project">{{ event.projectKey }}</span>
          </div>
          <div class="event-right">
            <span class="event-message">{{ getSummaryMessage(event) }}</span>
            <el-tag :type="getStatusType(event.status)" size="small">
              {{ event.status }}
            </el-tag>
            <el-icon class="expand-icon" :class="{ rotated: expandedEvents.has(event.id) }">
              <ArrowDown />
            </el-icon>
          </div>
        </div>

        <div v-if="expandedEvents.has(event.id)" class="event-details">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="Event Type">
              {{ formatEventType(event.eventType) }}
            </el-descriptions-item>
            <el-descriptions-item label="Duration" v-if="event.durationSeconds">
              {{ event.durationSeconds }}s
            </el-descriptions-item>
            <el-descriptions-item label="Statistics" :span="2" v-if="event.statistics && hasStatistics(event.statistics)">
              <span v-if="event.statistics.branchesCreated" style="color: #67c23a; margin-right: 12px;">
                +{{ event.statistics.branchesCreated }} branches
              </span>
              <span v-if="event.statistics.branchesUpdated" style="color: #409eff; margin-right: 12px;">
                ~{{ event.statistics.branchesUpdated }} branches
              </span>
              <span v-if="event.statistics.branchesDeleted" style="color: #f56c6c; margin-right: 12px;">
                -{{ event.statistics.branchesDeleted }} branches
              </span>
              <span v-if="event.statistics.commitsPushed" style="color: #909399;">
                {{ event.statistics.commitsPushed }} commits
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="Error" :span="2" v-if="event.errorMessage">
              <span style="color: #f56c6c;">{{ event.errorMessage }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-empty description="No recent activities" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { RecentEvent } from '@/types'
import { Clock, Check, Close, Warning, ArrowDown, Refresh } from '@element-plus/icons-vue'

interface Props {
  events: RecentEvent[] | null
}

const props = defineProps<Props>()
const selectedType = ref('')
const selectedStatus = ref('')
const expandedEvents = ref<Set<number>>(new Set())

const sortedEvents = computed(() => {
  if (!props.events) return []
  let events = props.events

  // Filter by type
  if (selectedType.value) {
    events = events.filter(e => e.eventType === selectedType.value)
  }

  // Filter by status
  if (selectedStatus.value) {
    events = events.filter(e => e.status.toLowerCase() === selectedStatus.value.toLowerCase())
  }

  // Sort by eventTime in reverse chronological order (newest first)
  return [...events].sort((a, b) =>
    new Date(b.eventTime).getTime() - new Date(a.eventTime).getTime()
  )
})

const toggleExpand = (eventId: number) => {
  if (expandedEvents.value.has(eventId)) {
    expandedEvents.value.delete(eventId)
  } else {
    expandedEvents.value.add(eventId)
  }
}

const getSummaryMessage = (event: RecentEvent): string => {
  // For successful events, show statistics summary
  if (event.status.toLowerCase() === 'success' && event.statistics) {
    const stats = event.statistics
    if (hasStatistics(stats)) {
      const parts: string[] = []
      if (stats.branchesCreated) parts.push(`+${stats.branchesCreated} ${stats.branchesCreated === 1 ? 'branch' : 'branches'}`)
      if (stats.branchesUpdated) parts.push(`~${stats.branchesUpdated} ${stats.branchesUpdated === 1 ? 'branch' : 'branches'}`)
      if (stats.branchesDeleted) parts.push(`-${stats.branchesDeleted} ${stats.branchesDeleted === 1 ? 'branch' : 'branches'}`)
      if (stats.commitsPushed) parts.push(`${stats.commitsPushed} ${stats.commitsPushed === 1 ? 'commit' : 'commits'}`)
      return parts.join('  ')
    }
    return 'No changes'
  }

  // For failed events, show error message
  if (event.status.toLowerCase() === 'failed' && event.errorMessage) {
    return event.errorMessage.length > 50
      ? event.errorMessage.substring(0, 50) + '...'
      : event.errorMessage
  }

  return formatEventType(event.eventType)
}

const hasStatistics = (stats: any): boolean => {
  if (!stats) return false
  return (stats.branchesCreated && stats.branchesCreated > 0) ||
         (stats.branchesUpdated && stats.branchesUpdated > 0) ||
         (stats.branchesDeleted && stats.branchesDeleted > 0) ||
         (stats.commitsPushed && stats.commitsPushed > 0)
}

const formatTime = (time: string) => {
  return new Date(time).toLocaleString('en-US', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getEventColor = (status: string) => {
  const colorMap: Record<string, string> = {
    'success': '#52c41a',
    'failed': '#f5222d',
    'running': '#1890ff'
  }
  return colorMap[status.toLowerCase()] || '#8c8c8c'
}

const getEventIcon = (eventType: string) => {
  const iconMap: Record<string, any> = {
    'sync_finished': Check,
    'sync_failed': Close,
    'webhook_sync': Refresh,
    'scheduled_sync': Clock,
    'manual_sync': Refresh,
    'task_blocked': Warning,
    'task_recovered': Check
  }
  return iconMap[eventType] || Refresh
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
</script>

<style scoped>
.activity-timeline {
  border-radius: 8px;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.filters {
  display: flex;
  gap: 12px;
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-item {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.event-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.event-item.expanded {
  border-color: #409eff;
  background-color: #f5f9ff;
}

.event-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.event-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.event-time {
  color: #909399;
  font-size: 12px;
  white-space: nowrap;
  min-width: 100px;
}

.event-project {
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 350px;
}

.event-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.event-message {
  color: #606266;
  font-size: 13px;
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.expand-icon {
  transition: transform 0.2s;
  color: #909399;
}

.expand-icon.rotated {
  transform: rotate(180deg);
}

.event-details {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e8e8e8;
}

.empty-state {
  padding: 40px 0;
}
</style>
