<template>
  <div class="projects">
    <!-- Filter Bar -->
    <FilterBar
      v-model="filters"
      :groups="groups"
      @search="loadProjects"
      @export="handleExport"
    />

    <!-- Batch Operation Bar -->
    <el-card v-if="selectedIds.length > 0" class="batch-bar" shadow="never">
      <div class="batch-content">
        <span class="batch-info">{{ selectedIds.length }} projects selected</span>
        <div class="batch-actions">
          <el-button size="small" @click="handleBatchSync">
            <el-icon><Refresh /></el-icon>
            Sync
          </el-button>
          <el-button size="small" @click="handleBatchPause">
            <el-icon><VideoPause /></el-icon>
            Pause
          </el-button>
          <el-button size="small" @click="handleBatchResume">
            <el-icon><VideoPlay /></el-icon>
            Resume
          </el-button>
          <el-button size="small" type="danger" @click="handleBatchDelete">
            <el-icon><Delete /></el-icon>
            Delete
          </el-button>
          <el-button size="small" type="warning" @click="handleBatchClearCache">
            <el-icon><Delete /></el-icon>
            Clear Cache
          </el-button>
          <el-button size="small" @click="handleClearSelection">Clear</el-button>
        </div>
      </div>
    </el-card>

    <!-- Projects Table -->
    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="projects"
        stripe
        style="width: 100%"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" />

        <el-table-column prop="projectKey" label="Project" min-width="250" sortable="custom">
          <template #default="{ row }">
            <div class="project-cell">
              <el-icon><FolderOpened /></el-icon>
              <a @click="handleViewDetail(row)" class="project-link">{{ row.projectKey }}</a>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Status" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.syncStatus)">
              {{ formatStatus(row.syncStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Sync" width="100">
          <template #default="{ row }">
            <el-tag
              v-if="row.lastSyncStatus"
              :type="getSyncStatusType(row.lastSyncStatus)"
              size="small"
              style="cursor: pointer"
              @click="handleShowSyncDetails(row)"
            >
              {{ formatLastSyncStatus(row.lastSyncStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column label="Failures" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.consecutiveFailures > 0" type="danger" size="small">
              {{ row.consecutiveFailures }}
            </el-tag>
            <span v-else style="color: #67c23a">0</span>
          </template>
        </el-table-column>

        <el-table-column label="Diff" width="100">
          <template #default="{ row }">
            <DiffBadge :diff="row.diff" />
          </template>
        </el-table-column>

        <el-table-column label="Delay" width="90" sortable="custom" prop="delaySeconds">
          <template #default="{ row }">
            <el-tag v-if="row.delayFormatted" :type="getDelayType(row.delaySeconds)" size="small">
              {{ row.delayFormatted }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="lastSyncAt" label="Last Sync" width="120" sortable="custom">
          <template #default="{ row }">
            {{ formatTime(row.lastSyncAt) }}
          </template>
        </el-table-column>

        <el-table-column prop="lastCommitTime" label="Last Commit" width="140" sortable="custom">
          <template #default="{ row }">
            {{ formatTime(row.lastCommitTime) }}
          </template>
        </el-table-column>

        <el-table-column label="Task" width="100" sortable="custom" prop="taskStatus">
          <template #default="{ row }">
            <el-tag v-if="row.taskStatus" :type="getTaskStatusType(row.taskStatus)" size="small">
              {{ formatTaskStatus(row.taskStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="lastCheckAt" label="Last Check" width="140" sortable="custom">
          <template #default="{ row }">
            {{ formatTime(row.lastCheckAt) }}
          </template>
        </el-table-column>

        <el-table-column label="Actions" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleViewDetail(row)">Detail</el-button>
            <el-button size="small" type="primary" @click="handleSync(row)">Sync</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadProjects"
          @current-change="loadProjects"
        />
      </div>
    </el-card>

    <!-- Sync Details Dialog -->
    <el-dialog v-model="syncDetailsVisible" title="Sync Details" width="700px">
      <div v-if="selectedProject" class="sync-details">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Project">{{ selectedProject.projectKey }}</el-descriptions-item>
          <el-descriptions-item label="Last Sync Status">
            <el-tag :type="getSyncStatusType(selectedProject.lastSyncStatus || '')">
              {{ formatLastSyncStatus(selectedProject.lastSyncStatus || '') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Consecutive Failures">
            <el-tag v-if="selectedProject.consecutiveFailures && selectedProject.consecutiveFailures > 0" type="danger">
              {{ selectedProject.consecutiveFailures }}
            </el-tag>
            <span v-else style="color: #67c23a">0</span>
          </el-descriptions-item>
          <el-descriptions-item label="Last Sync At">
            {{ formatTime(selectedProject.lastSyncAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="Project Status">
            <el-tag :type="getStatusType(selectedProject.syncStatus)">
              {{ formatStatus(selectedProject.syncStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="selectedProject.lastSyncSummary && selectedProject.lastSyncSummary.trim()" label="Sync Summary">
            <div style="white-space: pre-wrap; word-break: break-word;">{{ selectedProject.lastSyncSummary }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="selectedProject.lastSyncErrorMessage && selectedProject.lastSyncErrorMessage.trim()" label="Error Message">
            <div style="white-space: pre-wrap; word-break: break-word; color: #f56c6c;">{{ selectedProject.lastSyncErrorMessage }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FilterBar from '@/components/projects/FilterBar.vue'
import DiffBadge from '@/components/projects/DiffBadge.vue'
import { projectsApi } from '@/api/projects'
import type { ProjectListItem } from '@/types'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const projects = ref<ProjectListItem[]>([])
const groups = ref<string[]>([])
const selectedIds = ref<number[]>([])
const syncDetailsVisible = ref(false)
const selectedProject = ref<ProjectListItem | null>(null)

const filters = reactive({
  group: (route.query.group as string) || '',
  status: (route.query.status as string) || '',
  taskStatus: '',
  diffStatus: '',
  delayRange: '',
  search: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const sortBy = ref('')
const sortOrder = ref<'asc' | 'desc'>('asc')

const loadProjects = async () => {
  loading.value = true
  try {
    const params = {
      ...filters,
      page: pagination.page,
      size: pagination.size,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value
    }

    const response = await projectsApi.getProjects(params)
    projects.value = response.data.items
    pagination.total = response.data.total
  } catch (error) {
    ElMessage.error('Failed to load projects')
    console.error('Load projects failed:', error)
  } finally {
    loading.value = false
  }
}

const loadGroups = async () => {
  try {
    const response = await projectsApi.getGroups()
    groups.value = response.data
  } catch (error) {
    console.error('Load groups failed:', error)
  }
}

const handleSelectionChange = (selection: ProjectListItem[]) => {
  selectedIds.value = selection.map(item => item.id)
}

const handleClearSelection = () => {
  selectedIds.value = []
}

const handleSortChange = ({ prop, order }: any) => {
  if (order) {
    sortBy.value = prop
    sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  } else {
    sortBy.value = ''
    sortOrder.value = 'asc'
  }
  loadProjects()
}

const handleViewDetail = (project: ProjectListItem) => {
  router.push(`/projects/${project.id}`)
}

const handleShowSyncDetails = async (project: ProjectListItem) => {
  try {
    // Fetch latest sync result from backend
    const response = await projectsApi.getSyncResult(project.id)

    if (response.success && response.data) {
      const syncResult = response.data
      // Update project with sync result data
      selectedProject.value = {
        ...project,
        lastSyncStatus: syncResult.syncStatus,
        lastSyncAt: syncResult.lastSyncAt,
        lastSyncSummary: syncResult.summary,
        lastSyncErrorMessage: syncResult.errorMessage
      }
    } else {
      // Fallback to project data if no sync result
      selectedProject.value = project
    }
    syncDetailsVisible.value = true
  } catch (error) {
    console.error('Failed to fetch sync result:', error)
    // Fallback to project data on error
    selectedProject.value = project
    syncDetailsVisible.value = true
  }
}

const handleSync = async (project: ProjectListItem) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure to sync project "${project.projectKey}"?`,
      'Confirm Sync',
      { type: 'warning' }
    )
    await projectsApi.batchSync([project.id])
    ElMessage.success('Sync triggered successfully')
    loadProjects()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to trigger sync')
    }
  }
}

const handleBatchSync = async () => {
  try {
    await ElMessageBox.confirm(
      `Are you sure to sync ${selectedIds.value.length} projects?`,
      'Confirm Batch Sync',
      { type: 'warning' }
    )
    await projectsApi.batchSync(selectedIds.value)
    ElMessage.success('Batch sync triggered successfully')
    handleClearSelection()
    loadProjects()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to trigger batch sync')
    }
  }
}

const handleBatchPause = async () => {
  try {
    await ElMessageBox.confirm(
      `Are you sure to pause ${selectedIds.value.length} projects?`,
      'Confirm Batch Pause',
      { type: 'warning' }
    )
    await projectsApi.batchPause(selectedIds.value)
    ElMessage.success('Projects paused successfully')
    handleClearSelection()
    loadProjects()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to pause projects')
    }
  }
}

const handleBatchResume = async () => {
  try {
    await ElMessageBox.confirm(
      `Are you sure to resume ${selectedIds.value.length} projects?`,
      'Confirm Batch Resume',
      { type: 'info' }
    )
    await projectsApi.batchResume(selectedIds.value)
    ElMessage.success('Projects resumed successfully')
    handleClearSelection()
    loadProjects()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to resume projects')
    }
  }
}

const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `Are you sure to delete ${selectedIds.value.length} projects? This action cannot be undone!`,
      'Confirm Batch Delete',
      { type: 'error' }
    )
    await projectsApi.batchDelete(selectedIds.value)
    ElMessage.success('Projects deleted successfully')
    handleClearSelection()
    loadProjects()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to delete projects')
    }
  }
}

const handleBatchClearCache = async () => {
  try {
    await ElMessageBox.confirm(
      `Are you sure to clear Git cache for ${selectedIds.value.length} projects?`,
      'Confirm Clear Cache',
      { type: 'warning' }
    )
    const response = await projectsApi.batchClearCache(selectedIds.value)
    if (response.data.success > 0) {
      ElMessage.success(`Cleared cache for ${response.data.success} projects`)
    }
    if (response.data.failed > 0) {
      ElMessage.warning(`Failed to clear cache for ${response.data.failed} projects`)
    }
    handleClearSelection()
    loadProjects()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to clear cache')
    }
  }
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'active': 'success',
    'synced': 'success',
    'warning': 'warning',
    'outdated': 'warning',
    'paused': 'info',
    'failed': 'danger',
    'missing': 'danger',
    'error': 'danger',
    'pending': 'warning',
    'discovered': 'info',
    'initializing': 'info',
    'disabled': 'info'
  }
  return typeMap[status.toLowerCase()] || 'info'
}

const getDelayType = (delaySeconds: number) => {
  if (delaySeconds < 3600) return 'success'
  if (delaySeconds < 86400) return 'warning'
  return 'danger'
}

const formatSyncMethod = (method: string) => {
  return method === 'push_mirror' ? 'Push Mirror' : 'Pull Sync'
}

const formatStatus = (status: string) => {
  if (!status) return status
  const statusLower = status.toLowerCase()
  const statusMap: Record<string, string> = {
    'discovered': 'Discovered',
    'initializing': 'Initializing',
    'active': 'Active',
    'warning': 'Warning',
    'error': 'Error',
    'failed': 'Failed',
    'missing': 'Missing',
    'disabled': 'Disabled',
    'deleted': 'Deleted',
    'paused': 'Paused',
    'pending': 'Pending'
  }
  return statusMap[statusLower] || status
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

const getSyncStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'success': 'success',
    'failed': 'danger',
    'skipped': 'info'
  }
  return typeMap[status.toLowerCase()] || 'info'
}

const formatLastSyncStatus = (status: string) => {
  if (!status) return '-'
  const statusLower = status.toLowerCase()
  const statusMap: Record<string, string> = {
    'success': 'Success',
    'failed': 'Failed',
    'skipped': 'Skipped'
  }
  return statusMap[statusLower] || status
}

const getTaskStatusType = (status: string) => {
  if (!status) return 'info'
  const statusLower = status.toLowerCase()
  const typeMap: Record<string, string> = {
    'waiting': 'info',
    'pending': 'info',
    'running': 'warning',
    'blocked': 'warning',
    'disabled': '',
    'failed': 'danger'
  }
  return typeMap[statusLower] || 'info'
}

const formatTaskStatus = (status: string) => {
  if (!status) return '-'
  const statusLower = status.toLowerCase()
  const statusMap: Record<string, string> = {
    'waiting': 'Waiting',
    'pending': 'Pending',
    'running': 'Running',
    'blocked': 'Blocked',
    'disabled': 'Disabled',
    'failed': 'Failed'
  }
  return statusMap[statusLower] || status
}


const handleExport = () => {
  try {
    // CSV headers
    const headers = [
      'Project',
      'Status',
      'Branch New',
      'Branch Deleted',
      'Branch Outdated',
      'Commit Diff',
      'Delay',
      'Sync Method',
      'Last Sync',
      'Last Commit'
    ]

    // Convert projects data to CSV rows
    const rows = projects.value.map(project => [
      project.projectKey,
      project.syncStatus,
      project.diff.branchNew.toString(),
      project.diff.branchDeleted.toString(),
      project.diff.branchOutdated.toString(),
      project.diff.commitDiff.toString(),
      project.delayFormatted,
      formatSyncMethod(project.syncMethod),
      formatTime(project.lastSyncAt),
      formatTime(project.lastCommitTime)
    ])

    // Create CSV content
    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.map(cell => `"${cell}"`).join(','))
    ].join('\n')

    // Create blob and download
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)

    link.setAttribute('href', url)
    link.setAttribute('download', `projects_${new Date().toISOString().split('T')[0]}.csv`)
    link.style.visibility = 'hidden'

    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)

    ElMessage.success(`Exported ${projects.value.length} projects`)
  } catch (error) {
    ElMessage.error('Failed to export projects')
    console.error('Export failed:', error)
  }
}

onMounted(() => {
  loadGroups()
  loadProjects()
})
</script>

<style scoped>
.projects {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.batch-bar {
  border-left: 4px solid #1890ff;
}

.batch-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.batch-info {
  font-weight: 600;
  color: #1890ff;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

.table-card {
  border-radius: 8px;
}

.project-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-link {
  color: #1890ff;
  cursor: pointer;
  font-weight: 500;
}

.project-link:hover {
  text-decoration: underline;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
