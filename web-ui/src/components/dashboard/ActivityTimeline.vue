<template>
  <el-card class="activity-timeline">
    <template #header>
      <div class="timeline-header">
        <span>Recent Activities</span>
        <el-select
          v-model="selectedType"
          placeholder="All Types"
          size="small"
          style="width: 150px"
          clearable
        >
          <el-option label="All Types" value="" />
          <el-option label="Sync Started" value="sync_started" />
          <el-option label="Sync Finished" value="sync_finished" />
          <el-option label="Sync Failed" value="sync_failed" />
          <el-option label="Push Detected" value="push_detected" />
        </el-select>
      </div>
    </template>
    <el-timeline v-if="filteredEvents && filteredEvents.length > 0">
      <el-timeline-item
        v-for="event in filteredEvents"
        :key="event.id"
        :timestamp="formatTime(event.eventTime)"
        :color="getEventColor(event.status)"
        placement="top"
      >
        <div class="event-content">
          <div class="event-header">
            <el-icon :size="16"><component :is="getEventIcon(event.eventType)" /></el-icon>
            <span class="event-project">{{ event.projectKey }}</span>
            <el-tag :type="getStatusType(event.status)" size="small">
              {{ event.status }}
            </el-tag>
          </div>
          <div class="event-type">{{ formatEventType(event.eventType) }}</div>
          <div v-if="event.durationSeconds" class="event-duration">
            Duration: {{ event.durationSeconds }}s
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <div v-else class="empty-state">
      <el-empty description="No recent activities" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { RecentEvent } from '@/types'
import { Clock, Check, Close, Warning } from '@element-plus/icons-vue'

interface Props {
  events: RecentEvent[] | null
}

const props = defineProps<Props>()
const selectedType = ref('')

const filteredEvents = computed(() => {
  if (!props.events) return []
  if (!selectedType.value) return props.events
  return props.events.filter(e => e.eventType === selectedType.value)
})

const formatTime = (time: string) => {
  return new Date(time).toLocaleString()
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
    'sync_started': Clock,
    'sync_finished': Check,
    'sync_failed': Close,
    'push_detected': Warning
  }
  return iconMap[eventType] || Clock
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

.event-content {
  padding: 8px 0;
}

.event-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.event-project {
  font-weight: 600;
  color: #1890ff;
  flex: 1;
}

.event-type {
  color: #666;
  font-size: 14px;
  margin-bottom: 4px;
}

.event-duration {
  color: #999;
  font-size: 12px;
}

.empty-state {
  padding: 40px 0;
}
</style>
