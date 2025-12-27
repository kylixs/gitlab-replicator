<template>
  <div class="project-detail">
    <!-- Breadcrumb -->
    <el-breadcrumb class="breadcrumb" separator="/">
      <el-breadcrumb-item :to="{ path: '/' }">Dashboard</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/projects' }">Projects</el-breadcrumb-item>
      <el-breadcrumb-item>{{ projectKey }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- Header with Actions -->
    <el-card class="header-card" shadow="never">
      <div class="header-content">
        <div class="project-info">
          <el-icon :size="32"><FolderOpened /></el-icon>
          <div>
            <h2>{{ projectKey }}</h2>
            <el-tag v-if="overview" :type="getStatusType(overview.project.syncStatus)">
              {{ overview.project.syncStatus }}
            </el-tag>
          </div>
        </div>
        <div class="action-buttons">
          <el-button type="primary" :loading="syncing" @click="handleSync">
            <el-icon><Refresh /></el-icon>
            Sync Now
          </el-button>
          <el-button @click="handleRefresh">
            <el-icon><RefreshRight /></el-icon>
            Refresh
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Loading State -->
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading" :size="48"><Loading /></el-icon>
      <p>Loading project details...</p>
    </div>

    <!-- Tabs -->
    <el-card v-else-if="overview" class="tabs-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="Overview" name="overview">
          <OverviewTab :overview="overview" />
        </el-tab-pane>
        <el-tab-pane label="Branches" name="branches">
          <BranchesTab :project-id="projectId" />
        </el-tab-pane>
        <el-tab-pane label="Sync Events" name="events">
          <EventsTab :project-id="projectId" :project-key="projectKey" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import OverviewTab from '@/components/project-detail/OverviewTab.vue'
import BranchesTab from '@/components/project-detail/BranchesTab.vue'
import EventsTab from '@/components/project-detail/EventsTab.vue'
import { projectsApi } from '@/api/projects'
import { useAutoRefreshTimer } from '@/composables/useAutoRefresh'
import type { ProjectOverview } from '@/types'

const route = useRoute()
const projectId = Number(route.params.id)

const loading = ref(false)
const syncing = ref(false)
const activeTab = ref('overview')
const overview = ref<ProjectOverview | null>(null)
const projectKey = ref('')

const loadOverview = async () => {
  loading.value = true
  try {
    const response = await projectsApi.getProjectOverview(projectId)
    overview.value = response.data
    projectKey.value = response.data.project.projectKey
  } catch (error) {
    ElMessage.error('Failed to load project overview')
    console.error('Load overview failed:', error)
  } finally {
    loading.value = false
  }
}

const handleSync = async () => {
  try {
    await ElMessageBox.confirm(
      `Are you sure to sync project "${projectKey.value}"?`,
      'Confirm Sync',
      { type: 'warning' }
    )
    syncing.value = true
    await projectsApi.batchSync([projectId])
    ElMessage.success({
      message: 'Sync task has been scheduled. The sync will execute shortly. You can check the task status in the overview.',
      duration: 5000
    })
    // Reload overview after 2 seconds to show updated task info
    setTimeout(() => loadOverview(), 2000)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to trigger sync')
    }
  } finally {
    syncing.value = false
  }
}

const handleRefresh = () => {
  loadOverview()
  ElMessage.success('Refreshed')
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'synced': 'success',
    'syncing': 'info',
    'outdated': 'warning',
    'paused': 'info',
    'failed': 'danger',
    'pending': 'warning'
  }
  return typeMap[status.toLowerCase()] || 'info'
}

// Use auto-refresh timer
const { startTimer, stopTimer } = useAutoRefreshTimer(loadOverview)

onMounted(() => {
  loadOverview()
  startTimer()
})

onUnmounted(() => {
  stopTimer()
})
</script>

<style scoped>
.project-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.breadcrumb {
  padding: 0 4px;
}

.header-card {
  border-radius: 8px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.project-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.project-info h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 600;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #666;
}

.loading-container p {
  margin-top: 16px;
  font-size: 16px;
}

.tabs-card {
  border-radius: 8px;
  min-height: 400px;
}

:deep(.el-tabs__content) {
  padding-top: 12px;
}

:deep(.el-card__body) {
  padding: 16px;
}
</style>
