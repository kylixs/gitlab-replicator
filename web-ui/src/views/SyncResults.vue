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
        <el-table-column label="Actions" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="viewDetails(row)"
            >
              Details
            </el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { syncApi, type SyncResult } from '@/api/sync'
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

const viewDetails = (row: SyncResult) => {
  router.push(`/projects/${row.syncProjectId}`)
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
</style>
