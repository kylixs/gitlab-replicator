<template>
  <div class="sync-results-container">
    <el-card shadow="never">
      <template #header>
        <div class="header-wrapper">
          <h2>Sync Results</h2>
          <el-space>
            <el-button
              type="primary"
              :icon="Refresh"
              @click="loadData"
              :loading="loading"
            >
              Refresh
            </el-button>
          </el-space>
        </div>
      </template>

      <!-- Filters -->
      <div class="filters">
        <el-form :inline="true" :model="filters">
          <el-form-item label="Status">
            <el-select
              v-model="filters.syncStatus"
              placeholder="All"
              clearable
              style="width: 150px"
              @change="handleFilterChange"
            >
              <el-option label="Success" value="success" />
              <el-option label="Failed" value="failed" />
              <el-option label="Skipped" value="skipped" />
            </el-select>
          </el-form-item>
          <el-form-item label="Search">
            <el-input
              v-model="filters.search"
              placeholder="Project key"
              clearable
              style="width: 300px"
              @change="handleFilterChange"
            />
          </el-form-item>
        </el-form>
      </div>

      <!-- Results Table -->
      <el-table
        :data="results"
        v-loading="loading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="projectKey" label="Project" min-width="200" />
        <el-table-column label="Sync Status" width="120">
          <template #default="{ row }">
            <el-tag
              :type="getSyncStatusType(row.syncStatus)"
              size="small"
            >
              {{ row.syncStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Changes" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.hasChanges" type="warning" size="small">
              {{ row.changesCount || 'Yes' }}
            </el-tag>
            <el-tag v-else type="info" size="small">No</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="syncMethod" label="Method" width="120" />
        <el-table-column label="Duration" width="100">
          <template #default="{ row }">
            {{ formatDuration(row.durationSeconds) }}
          </template>
        </el-table-column>
        <el-table-column label="Last Sync" width="180">
          <template #default="{ row }">
            {{ formatTime(row.lastSyncAt) }}
          </template>
        </el-table-column>
        <el-table-column label="Commits" width="180">
          <template #default="{ row }">
            <div v-if="row.sourceCommitSha">
              <el-tooltip :content="row.sourceCommitSha">
                <code style="font-size: 12px">{{ row.sourceCommitSha.substring(0, 8) }}</code>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Summary" min-width="200">
          <template #default="{ row }">
            <div v-if="row.errorMessage" class="error-text">
              {{ row.errorMessage }}
            </div>
            <div v-else>{{ row.summary || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="200" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button
                type="primary"
                link
                size="small"
                @click="viewDetail(row)"
              >
                Detail
              </el-button>
              <el-button
                type="primary"
                link
                size="small"
                @click="viewLogs(row)"
              >
                Logs
              </el-button>
              <el-button
                type="primary"
                link
                size="small"
                @click="viewProjectDetail(row)"
              >
                Project
              </el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- Sync Logs Dialog -->
    <el-dialog
      v-model="logsDialogVisible"
      title="Sync Logs"
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-loading="logsLoading" class="logs-content">
        <el-empty v-if="!logsLoading && syncLogs.length === 0" description="No sync events found" />

        <el-timeline v-else>
          <el-timeline-item
            v-for="log in syncLogs"
            :key="log.id"
            :timestamp="formatTime(log.createdAt)"
            :type="getLogType(log.status)"
            placement="top"
          >
            <el-card>
              <template #header>
                <div class="log-header">
                  <span class="log-event-type">{{ log.eventType }}</span>
                  <el-tag :type="getStatusTagType(log.status)" size="small">
                    {{ log.status }}
                  </el-tag>
                </div>
              </template>
              <div class="log-message">{{ log.message }}</div>
              <div v-if="log.durationMs" class="log-duration">
                Duration: {{ formatDuration(log.durationMs / 1000) }}
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>

    <!-- Sync Result Detail Dialog -->
    <el-dialog
      v-model="detailDialogVisible"
      title="Sync Result Detail"
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-loading="detailLoading">
        <div v-if="resultDetail">
          <!-- Basic Information -->
          <el-descriptions :column="2" border>
            <el-descriptions-item label="Project">
              {{ resultDetail.projectKey }}
            </el-descriptions-item>
            <el-descriptions-item label="Sync Method">
              {{ resultDetail.syncMethod }}
            </el-descriptions-item>
            <el-descriptions-item label="Status">
              <el-tag :type="getSyncStatusType(resultDetail.syncStatus)">
                {{ resultDetail.syncStatus }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Changes">
              <el-tag v-if="resultDetail.hasChanges" type="warning">
                {{ resultDetail.changesCount }} changes
              </el-tag>
              <el-tag v-else type="info">No changes</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Duration">
              {{ formatDuration(resultDetail.durationSeconds) }}
            </el-descriptions-item>
            <el-descriptions-item label="Last Sync">
              {{ formatTime(resultDetail.lastSyncAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="Source Commit" :span="2">
              <code v-if="resultDetail.sourceCommitSha">{{ resultDetail.sourceCommitSha }}</code>
              <span v-else>-</span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- Summary -->
          <el-divider />
          <h3>Summary</h3>
          <div v-if="resultDetail.errorMessage" class="error-text">
            {{ resultDetail.errorMessage }}
          </div>
          <div v-else>{{ resultDetail.summary || 'No summary available' }}</div>

          <!-- Branch Information -->
          <el-divider />
          <h3>Branch Information</h3>
          <div class="branch-summary">
            <el-tag type="info" size="large">
              Total Branches: {{ resultDetail.totalBranches }}
            </el-tag>
          </div>

          <h4 style="margin-top: 20px">Recent 10 Branches</h4>
          <el-table :data="resultDetail.recentBranches" stripe style="width: 100%">
            <el-table-column prop="branchName" label="Branch" min-width="150">
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
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { syncApi, type SyncResult, type SyncResultDetail } from '@/api/sync'
import { ElMessage } from 'element-plus'
import { useAutoRefreshTimer } from '@/composables/useAutoRefresh'

const router = useRouter()

const loading = ref(false)
const results = ref<SyncResult[]>([])

const filters = ref({
  syncStatus: '',
  search: ''
})

const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const response = await syncApi.getSyncResults({
      syncStatus: filters.value.syncStatus || undefined,
      search: filters.value.search || undefined,
      page: pagination.value.page,
      size: pagination.value.size
    })

    if (response.success && response.data) {
      results.value = response.data.items || []
      pagination.value.total = response.data.total || 0
    } else {
      ElMessage.error(response.message || 'Failed to load sync results')
    }
  } catch (error: any) {
    console.error('Load sync results failed:', error)
    ElMessage.error(error.message || 'Failed to load sync results')
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => {
  pagination.value.page = 1
  loadData()
}

const getSyncStatusType = (status: string) => {
  switch (status) {
    case 'success':
      return 'success'
    case 'failed':
      return 'danger'
    case 'skipped':
      return 'info'
    default:
      return ''
  }
}

const formatDuration = (seconds: number | null | undefined) => {
  if (!seconds) return '-'
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${minutes}m ${secs}s`
}

const formatTime = (time: string | null | undefined) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString()
}

const viewProjectDetail = (row: SyncResult) => {
  router.push(`/projects/${row.syncProjectId}`)
}

// Sync result detail dialog
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const resultDetail = ref<SyncResultDetail | null>(null)

const viewDetail = async (row: SyncResult) => {
  detailDialogVisible.value = true
  detailLoading.value = true
  resultDetail.value = null

  try {
    const response = await syncApi.getSyncResultDetail(row.id)
    if (response.success && response.data) {
      resultDetail.value = response.data
    } else {
      ElMessage.error(response.message || 'Failed to load result detail')
    }
  } catch (error: any) {
    console.error('Load result detail failed:', error)
    ElMessage.error(error.message || 'Failed to load result detail')
  } finally {
    detailLoading.value = false
  }
}

// Sync logs dialog
const logsDialogVisible = ref(false)
const logsLoading = ref(false)
const syncLogs = ref<any[]>([])

const viewLogs = async (row: SyncResult) => {
  logsDialogVisible.value = true
  logsLoading.value = true
  syncLogs.value = []

  try {
    // Filter events by time range of this specific sync task
    const startDate = row.startedAt ? new Date(row.startedAt).toISOString().split('T')[0] : undefined
    const endDate = row.completedAt ? new Date(row.completedAt).toISOString().split('T')[0] : undefined

    const response = await syncApi.getEvents({
      projectId: row.syncProjectId,
      startDate: startDate,
      endDate: endDate,
      page: 1,
      size: 50
    })

    if (response.success && response.data) {
      // Further filter by exact time range to get only events for this sync task
      const startTime = row.startedAt ? new Date(row.startedAt).getTime() : 0
      const endTime = row.completedAt ? new Date(row.completedAt).getTime() : Date.now()

      syncLogs.value = (response.data.items || []).filter((log: any) => {
        const logTime = new Date(log.createdAt).getTime()
        return logTime >= startTime && logTime <= endTime
      })
    } else {
      ElMessage.error(response.message || 'Failed to load sync logs')
    }
  } catch (error: any) {
    console.error('Load sync logs failed:', error)
    ElMessage.error(error.message || 'Failed to load sync logs')
  } finally {
    logsLoading.value = false
  }
}

const getLogType = (status: string) => {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'SKIPPED':
      return 'info'
    default:
      return 'primary'
  }
}

const getStatusTagType = (status: string) => {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'SKIPPED':
      return 'info'
    default:
      return ''
  }
}

// Auto-refresh timer
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
.sync-results-container {
  padding: 20px;
}

.header-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-wrapper h2 {
  margin: 0;
  font-size: 20px;
}

.filters {
  margin-bottom: 20px;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.logs-content {
  max-height: 600px;
  overflow-y: auto;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-event-type {
  font-weight: bold;
  color: #303133;
}

.log-message {
  margin: 8px 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}

.log-duration {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.branch-summary {
  margin: 16px 0;
}

</style>
