<template>
  <div class="dashboard">
    <!-- Action Bar -->
    <el-card class="action-bar" shadow="never">
      <el-space>
        <el-button
          type="primary"
          :loading="scanningIncremental"
          @click="handleIncrementalScan"
        >
          <el-icon><Refresh /></el-icon>
          Incremental Scan
        </el-button>
        <el-button
          type="warning"
          :loading="scanningFull"
          @click="handleFullScan"
        >
          <el-icon><FolderOpened /></el-icon>
          Full Scan
        </el-button>
      </el-space>
    </el-card>

    <!-- Statistics Cards -->
    <el-row :gutter="24" class="stat-cards">
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
    <el-row :gutter="24" class="content-row">
      <el-col :xs="24" :lg="12">
        <StatusChart :data="distribution" />
      </el-col>
      <el-col :xs="24" :lg="12">
        <DelayedTable
          :projects="delayedProjects"
          @view-detail="handleViewDetail"
          @sync="handleSync"
        />
      </el-col>
    </el-row>

    <!-- Activity Timeline -->
    <el-row :gutter="24">
      <el-col :span="24">
        <ActivityTimeline :events="recentEvents" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatCard from '@/components/dashboard/StatCard.vue'
import StatusChart from '@/components/dashboard/StatusChart.vue'
import DelayedTable from '@/components/dashboard/DelayedTable.vue'
import ActivityTimeline from '@/components/dashboard/ActivityTimeline.vue'
import { dashboardApi } from '@/api/dashboard'
import { syncApi } from '@/api/sync'
import { useAutoRefreshTimer } from '@/composables/useAutoRefresh'
import type {
  DashboardStats,
  StatusDistribution,
  DelayedProject,
  RecentEvent
} from '@/types'

const router = useRouter()
const stats = ref<DashboardStats | null>(null)
const distribution = ref<StatusDistribution | null>(null)
const delayedProjects = ref<DelayedProject[] | null>(null)
const recentEvents = ref<RecentEvent[] | null>(null)
const scanningIncremental = ref(false)
const scanningFull = ref(false)

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
  } catch (error) {
    ElMessage.error('Failed to load dashboard data')
    console.error('Load dashboard data failed:', error)
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

const handleIncrementalScan = async () => {
  try {
    await ElMessageBox.confirm(
      'This will trigger an incremental scan to detect project changes. Continue?',
      'Confirm Incremental Scan',
      {
        confirmButtonText: 'Scan',
        cancelButtonText: 'Cancel',
        type: 'info'
      }
    )

    scanningIncremental.value = true
    const response = await syncApi.triggerScan('incremental')

    if (response.success) {
      const result = response.data
      ElMessage.success(
        `Scan completed: ${result.scannedCount} projects scanned, ` +
        `${result.addedCount} added, ${result.updatedCount} updated`
      )
      // Refresh dashboard data
      await loadData()
    } else {
      ElMessage.error('Scan failed: ' + (response.message || 'Unknown error'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to trigger incremental scan')
      console.error('Incremental scan failed:', error)
    }
  } finally {
    scanningIncremental.value = false
  }
}

const handleFullScan = async () => {
  try {
    await ElMessageBox.confirm(
      'This will trigger a full scan of all projects. This may take several minutes. Continue?',
      'Confirm Full Scan',
      {
        confirmButtonText: 'Scan',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )

    scanningFull.value = true
    const response = await syncApi.triggerScan('full')

    if (response.success) {
      const result = response.data
      ElMessage.success(
        `Full scan completed: ${result.scannedCount} projects scanned, ` +
        `${result.addedCount} added, ${result.updatedCount} updated, ${result.removedCount} removed`
      )
      // Refresh dashboard data
      await loadData()
    } else {
      ElMessage.error('Full scan failed: ' + (response.message || 'Unknown error'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to trigger full scan')
      console.error('Full scan failed:', error)
    }
  } finally {
    scanningFull.value = false
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
  gap: 24px;
}

.action-bar {
  margin-bottom: 0;
}

.action-bar :deep(.el-card__body) {
  padding: 16px;
}

.stat-cards {
  margin-bottom: 0;
}

.content-row {
  margin-bottom: 0;
}

.stat-cards :deep(.el-col) {
  margin-bottom: 24px;
}

.content-row :deep(.el-col) {
  margin-bottom: 24px;
}
</style>
