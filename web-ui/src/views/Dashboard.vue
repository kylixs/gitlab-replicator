<template>
  <div class="dashboard">
    <!-- Today's Sync Statistics -->
    <el-card class="sync-stats-card" shadow="never">
      <template #header>
        <span>Today's Sync Statistics</span>
      </template>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">Total Syncs</div>
            <div class="stat-value">{{ todayStats?.totalSyncs || 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">Successful</div>
            <div class="stat-value success">{{ todayStats?.successSyncs || 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">Failed</div>
            <div class="stat-value danger">{{ todayStats?.failedSyncs || 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">Branches Changed</div>
            <div class="stat-value info">{{ todayStats?.totalBranchChanges || 0 }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- Statistics Cards -->
    <el-row :gutter="16" class="stat-cards">
      <!-- Total Projects Card -->
      <el-col :xs="24" :sm="12" :md="8" :lg="24 / 5">
        <StatCard
          title="Total Projects"
          :value="stats?.totalProjects || 0"
          icon="FolderOpened"
          type="primary"
          @click="navigateToProjects()"
        />
      </el-col>

      <!-- Dynamic Status Cards -->
      <el-col
        v-for="statusItem in statusCards"
        :key="statusItem.status"
        :xs="24" :sm="12" :md="8" :lg="24 / 5"
      >
        <StatCard
          :title="formatStatusName(statusItem.status)"
          :value="statusItem.count"
          :icon="getStatusIcon(statusItem.status)"
          :type="getStatusType(statusItem.status)"
          @click="navigateToProjects(statusItem.status)"
        />
      </el-col>
    </el-row>

    <!-- Charts and Tables Row -->
    <el-row :gutter="16" class="content-row">
      <el-col :xs="24" :lg="8">
        <StatusChart :data="distribution" />
      </el-col>
      <el-col :xs="24" :lg="16">
        <SyncTrendChart
          :trend-data="trendData"
          :trend-data24h="trendData24h"
          @time-range-change="handleSyncTrendTimeRangeChange"
        />
      </el-col>
    </el-row>

    <!-- Event Type Trend Chart -->
    <el-row :gutter="16" class="content-row">
      <el-col :span="24">
        <EventTypeTrendChart
          :event-type-trend="eventTypeTrend"
          :event-type-trend24h="eventTypeTrend24h"
          @time-range-change="handleEventTypeTrendTimeRangeChange"
        />
      </el-col>
    </el-row>

    <!-- Delayed Projects Table -->
    <el-row :gutter="16" class="content-row">
      <el-col :span="24">
        <DelayedTable
          :projects="delayedProjects"
          @view-detail="handleViewDetail"
          @sync="handleSync"
        />
      </el-col>
    </el-row>

    <!-- Activity Timeline -->
    <el-row :gutter="16">
      <el-col :span="24">
        <ActivityTimeline :events="recentEvents" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatCard from '@/components/dashboard/StatCard.vue'
import StatusChart from '@/components/dashboard/StatusChart.vue'
import SyncTrendChart from '@/components/dashboard/SyncTrendChart.vue'
import EventTypeTrendChart from '@/components/dashboard/EventTypeTrendChart.vue'
import DelayedTable from '@/components/dashboard/DelayedTable.vue'
import ActivityTimeline from '@/components/dashboard/ActivityTimeline.vue'
import { dashboardApi, type EventTypeTrend as ApiEventTypeTrend } from '@/api/dashboard'
import { useAutoRefreshTimer } from '@/composables/useAutoRefresh'
import type {
  DashboardStats,
  StatusDistribution,
  DelayedProject,
  RecentEvent
} from '@/types'

interface TodaySyncStats {
  totalSyncs: number
  successSyncs: number
  failedSyncs: number
  totalBranchChanges: number
}

interface TrendData {
  dates: string[]
  totalSyncs: number[]
  successSyncs: number[]
  failedSyncs: number[]
}

interface TrendData24h {
  hours: string[]
  totalSyncs: number[]
  successSyncs: number[]
  failedSyncs: number[]
}

const router = useRouter()
const stats = ref<DashboardStats | null>(null)
const distribution = ref<StatusDistribution | null>(null)
const delayedProjects = ref<DelayedProject[] | null>(null)
const recentEvents = ref<RecentEvent[] | null>(null)
const todayStats = ref<TodaySyncStats | null>(null)
const trendData = ref<TrendData | null>(null)
const trendData24h = ref<TrendData24h | null>(null)
const eventTypeTrend = ref<ApiEventTypeTrend | null>(null)
const eventTypeTrend24h = ref<ApiEventTypeTrend | null>(null)

// Compute dynamic status cards from statusCounts
const statusCards = computed(() => {
  if (!stats.value?.statusCounts) return []

  return Object.entries(stats.value.statusCounts)
    .map(([status, count]) => ({
      status,
      count: Number(count)
    }))
    .sort((a, b) => b.count - a.count) // Sort by count descending
})

// Format status name for display
const formatStatusName = (status: string): string => {
  const nameMap: Record<string, string> = {
    'active': 'Active',
    'pending': 'Pending',
    'missing': 'Missing',
    'failed': 'Failed',
    'warning': 'Warning',
    'deleted': 'Deleted',
    'target_created': 'Target Created',
    'mirror_configured': 'Mirror Configured'
  }
  return nameMap[status] || status.charAt(0).toUpperCase() + status.slice(1).replace(/_/g, ' ')
}

// Get icon for status
const getStatusIcon = (status: string): string => {
  const iconMap: Record<string, string> = {
    'active': 'Check',
    'pending': 'Clock',
    'missing': 'QuestionFilled',
    'failed': 'Close',
    'warning': 'Warning',
    'deleted': 'Delete',
    'target_created': 'FolderAdd',
    'mirror_configured': 'Setting'
  }
  return iconMap[status] || 'Document'
}

// Get card type (color) for status
const getStatusType = (status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  const typeMap: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    'active': 'success',
    'pending': 'info',
    'missing': 'warning',
    'failed': 'danger',
    'warning': 'warning',
    'deleted': 'info',
    'target_created': 'primary',
    'mirror_configured': 'primary'
  }
  return typeMap[status] || 'info'
}

const loadData = async () => {
  try {
    const [statsRes, distRes, delayedRes, eventsRes] = await Promise.all([
      dashboardApi.getStats(),
      dashboardApi.getStatusDistribution(),
      dashboardApi.getTopDelayedProjects(10),
      dashboardApi.getRecentEvents(20)
    ])

    stats.value = statsRes.data
    distribution.value = distRes.data
    delayedProjects.value = delayedRes.data
    recentEvents.value = eventsRes.data

    // Load today's sync statistics
    await loadTodayStats()

    // Load trend data for past 7 days
    await loadTrendData()

    // Load event type trend data
    await loadEventTypeTrend()
  } catch (error) {
    ElMessage.error('Failed to load dashboard data')
    console.error('Load dashboard data failed:', error)
  }
}

const loadTodayStats = async () => {
  try {
    const response = await dashboardApi.getTodayStats()
    todayStats.value = response.data
  } catch (error) {
    console.error('Failed to load today stats:', error)
    ElMessage.error('Failed to load today\'s sync statistics')
  }
}

const loadTrendData = async () => {
  try {
    const response = await dashboardApi.getTrend('7d')
    trendData.value = response.data
  } catch (error) {
    console.error('Failed to load trend data:', error)
    ElMessage.error('Failed to load trend data')
  }
}

const loadTrendData24h = async () => {
  try {
    const response = await dashboardApi.getTrend('24h')
    // Map to TrendData24h format (dates -> hours)
    trendData24h.value = {
      hours: response.data.dates,
      totalSyncs: response.data.totalSyncs,
      successSyncs: response.data.successSyncs,
      failedSyncs: response.data.failedSyncs
    }
  } catch (error) {
    console.error('Failed to load 24h trend data:', error)
    ElMessage.error('Failed to load 24h trend data')
  }
}

const loadEventTypeTrend = async () => {
  try {
    const response = await dashboardApi.getEventTypeTrend('7d')
    eventTypeTrend.value = response.data
  } catch (error) {
    console.error('Failed to load event type trend data:', error)
    ElMessage.error('Failed to load event type trend data')
  }
}

const loadEventTypeTrend24h = async () => {
  try {
    const response = await dashboardApi.getEventTypeTrend('24h')
    eventTypeTrend24h.value = response.data
  } catch (error) {
    console.error('Failed to load 24h event type trend data:', error)
    ElMessage.error('Failed to load 24h event type trend data')
  }
}

const handleSyncTrendTimeRangeChange = async (range: '24h' | '7d') => {
  if (range === '24h' && !trendData24h.value) {
    await loadTrendData24h()
  } else if (range === '7d' && !trendData.value) {
    await loadTrendData()
  }
}

const handleEventTypeTrendTimeRangeChange = async (range: '24h' | '7d') => {
  if (range === '24h' && !eventTypeTrend24h.value) {
    await loadEventTypeTrend24h()
  } else if (range === '7d' && !eventTypeTrend.value) {
    await loadEventTypeTrend()
  }
}

const navigateToProjects = (status?: string) => {
  router.push({
    path: '/projects',
    query: status ? { status } : {}
  })
}

const handleViewDetail = (project: DelayedProject) => {
  router.push(`/projects/${project.syncProjectId}`)
}

const handleSync = async (project: DelayedProject) => {
  try {
    ElMessage.info(`Triggering sync for ${project.projectKey}...`)
    // TODO: Implement sync API call
  } catch (error) {
    ElMessage.error('Failed to trigger sync')
  }
}

// Use auto-refresh timer
const { startTimer, stopTimer } = useAutoRefreshTimer(loadData)

onMounted(() => {
  loadData()
  startTimer()
})

onUnmounted(() => {
  stopTimer()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sync-stats-card {
  margin-bottom: 0;
}

.sync-stats-card :deep(.el-card__header) {
  padding: 16px 20px;
  font-weight: 600;
}

.stat-item {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-value.success {
  color: #67c23a;
}

.stat-value.danger {
  color: #f56c6c;
}

.stat-value.info {
  color: #409eff;
}

.stat-cards {
  margin-bottom: 0;
}

.content-row {
  margin-bottom: 0;
}

.stat-cards :deep(.el-col) {
  margin-bottom: 16px;
}

.content-row :deep(.el-col) {
  margin-bottom: 16px;
}
</style>
