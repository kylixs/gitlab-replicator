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

        <el-table-column label="Status" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.syncStatus)">
              {{ row.syncStatus }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Diff" width="200">
          <template #default="{ row }">
            <DiffBadge :diff="row.diff" />
          </template>
        </el-table-column>

        <el-table-column label="Delay" width="120" sortable="custom" prop="delaySeconds">
          <template #default="{ row }">
            <el-tag :type="getDelayType(row.delaySeconds)">
              {{ row.delayFormatted }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="syncMethod" label="Sync Method" width="130">
          <template #default="{ row }">
            <el-tag type="info" size="small">
              {{ formatSyncMethod(row.syncMethod) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="lastSyncAt" label="Last Sync" width="180" sortable="custom">
          <template #default="{ row }">
            {{ formatTime(row.lastSyncAt) }}
          </template>
        </el-table-column>

        <el-table-column prop="updatedAt" label="Updated" width="180" sortable="custom">
          <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
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

const filters = reactive({
  group: (route.query.group as string) || '',
  status: (route.query.status as string) || '',
  syncMethod: '',
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

const getDelayType = (delaySeconds: number) => {
  if (delaySeconds < 3600) return 'success'
  if (delaySeconds < 86400) return 'warning'
  return 'danger'
}

const formatSyncMethod = (method: string) => {
  return method === 'push_mirror' ? 'Push Mirror' : 'Pull Sync'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
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
      'Updated'
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
      formatTime(project.updatedAt)
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
