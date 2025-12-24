<template>
  <div class="dashboard">
    <!-- Statistics Cards -->
    <el-row :gutter="24" class="stat-cards">
      <el-col :xs="24" :sm="12" :md="8" :lg="24 / 5">
        <StatCard
          title="Total Projects"
          :value="stats?.totalProjects || 0"
          icon="FolderOpened"
          type="primary"
          @click="navigateToProjects()"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="8" :lg="24 / 5">
        <StatCard
          title="Synced"
          :value="stats?.syncedProjects || 0"
          icon="Check"
          type="success"
          @click="navigateToProjects('synced')"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="8" :lg="24 / 5">
        <StatCard
          title="Syncing"
          :value="stats?.syncingProjects || 0"
          icon="Loading"
          type="primary"
          @click="navigateToProjects('syncing')"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="8" :lg="24 / 5">
        <StatCard
          title="Paused"
          :value="stats?.pausedProjects || 0"
          icon="VideoPause"
          type="info"
          @click="navigateToProjects('paused')"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="8" :lg="24 / 5">
        <StatCard
          title="Failed"
          :value="stats?.failedProjects || 0"
          icon="Close"
          type="danger"
          @click="navigateToProjects('failed')"
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatCard from '@/components/dashboard/StatCard.vue'
import StatusChart from '@/components/dashboard/StatusChart.vue'
import DelayedTable from '@/components/dashboard/DelayedTable.vue'
import ActivityTimeline from '@/components/dashboard/ActivityTimeline.vue'
import { dashboardApi } from '@/api/dashboard'
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

let refreshTimer: number | null = null

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

const startAutoRefresh = () => {
  // Refresh every 30 seconds
  refreshTimer = window.setInterval(() => {
    loadData()
  }, 30000)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  loadData()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
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
