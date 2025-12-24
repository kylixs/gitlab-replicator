<template>
  <el-row :gutter="16" class="event-stats">
    <el-col :xs="24" :sm="12" :md="6">
      <el-card shadow="hover">
        <div class="stat-item">
          <div class="stat-icon primary">
            <el-icon :size="24"><List /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">Total Events</div>
            <div class="stat-value">{{ stats?.totalEvents || 0 }}</div>
          </div>
        </div>
      </el-card>
    </el-col>
    <el-col :xs="24" :sm="12" :md="6">
      <el-card shadow="hover">
        <div class="stat-item">
          <div class="stat-icon success">
            <el-icon :size="24"><Check /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">Success</div>
            <div class="stat-value">{{ stats?.successEvents || 0 }}</div>
          </div>
        </div>
      </el-card>
    </el-col>
    <el-col :xs="24" :sm="12" :md="6">
      <el-card shadow="hover">
        <div class="stat-item">
          <div class="stat-icon danger">
            <el-icon :size="24"><Close /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">Failed</div>
            <div class="stat-value">{{ stats?.failedEvents || 0 }}</div>
          </div>
        </div>
      </el-card>
    </el-col>
    <el-col :xs="24" :sm="12" :md="6">
      <el-card shadow="hover">
        <div class="stat-item">
          <div class="stat-icon info">
            <el-icon :size="24"><Timer /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">Avg Duration</div>
            <div class="stat-value">{{ formatDuration(stats?.avgDurationMs) }}</div>
          </div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import type { EventStats } from '@/types'

interface Props {
  stats: EventStats | null
}

defineProps<Props>()

const formatDuration = (ms: number | undefined) => {
  if (!ms) return '0s'
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}m ${remainingSeconds}s`
}
</script>

<style scoped>
.event-stats {
  margin-bottom: 16px;
}

.event-stats :deep(.el-col) {
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon.primary {
  background-color: #e6f7ff;
  color: #1890ff;
}

.stat-icon.success {
  background-color: #f6ffed;
  color: #52c41a;
}

.stat-icon.danger {
  background-color: #fff1f0;
  color: #f5222d;
}

.stat-icon.info {
  background-color: #f0f5ff;
  color: #597ef7;
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
  font-size: 24px;
  font-weight: bold;
  color: #333;
}
</style>
