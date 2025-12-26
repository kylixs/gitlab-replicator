<template>
  <div class="branches-tab">
    <!-- Statistics Cards -->
    <el-row :gutter="16" class="branch-stats">
      <el-col :xs="24" :sm="12" :md:="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon success">
              <el-icon :size="24"><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Synced</div>
              <div class="stat-value">{{ comparison?.syncedCount || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon warning">
              <el-icon :size="24"><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Outdated</div>
              <div class="stat-value">{{ comparison?.outdatedCount || 0 }}</div>
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
              <div class="stat-label">Missing</div>
              <div class="stat-value">{{ comparison?.missingInTargetCount || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon info">
              <el-icon :size="24"><Plus /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Extra</div>
              <div class="stat-value">{{ comparison?.extraInTargetCount || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Filter Bar -->
    <el-card shadow="never" class="filter-bar">
      <el-form :inline="true">
        <el-form-item label="Status">
          <el-select
            v-model="statusFilter"
            placeholder="All Status"
            clearable
            style="width: 180px"
            @change="filterBranches"
          >
            <el-option label="Synced" value="synced" />
            <el-option label="Outdated" value="outdated" />
            <el-option label="Missing in Target" value="missing_in_target" />
            <el-option label="Extra in Target" value="extra_in_target" />
          </el-select>
        </el-form-item>
        <el-form-item label="Search">
          <el-input
            v-model="searchText"
            placeholder="Branch name"
            clearable
            style="width: 200px"
            @input="filterBranches"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Branches Table -->
    <el-card shadow="hover" class="branches-table">
      <el-table
        v-loading="loading"
        :data="filteredBranches"
        stripe
        style="width: 100%"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="branch-details">
              <el-row :gutter="24">
                <!-- Source Branch Details -->
                <el-col :span="12">
                  <h4>Source Branch</h4>
                  <el-descriptions :column="1" border size="small">
                    <el-descriptions-item label="Commit SHA">
                      <code v-if="row.sourceCommitId" class="commit-hash-full">{{ row.sourceCommitId }}</code>
                      <span v-else class="not-available">-</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="Commit Time">
                      {{ formatTime(row.sourceLastCommitAt) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="Author">
                      <span v-if="row.sourceCommitAuthor">{{ row.sourceCommitAuthor }}</span>
                      <span v-else class="not-available">-</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="Message">
                      <div v-if="row.sourceCommitMessage" class="commit-message">
                        {{ row.sourceCommitMessage }}
                      </div>
                      <span v-else class="not-available">-</span>
                    </el-descriptions-item>
                  </el-descriptions>
                </el-col>

                <!-- Target Branch Details -->
                <el-col :span="12">
                  <h4>Target Branch</h4>
                  <el-descriptions :column="1" border size="small">
                    <el-descriptions-item label="Commit SHA">
                      <code v-if="row.targetCommitId" class="commit-hash-full">{{ row.targetCommitId }}</code>
                      <span v-else class="not-available">-</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="Commit Time">
                      {{ formatTime(row.targetLastCommitAt) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="Author">
                      <span v-if="row.targetCommitAuthor">{{ row.targetCommitAuthor }}</span>
                      <span v-else class="not-available">-</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="Message">
                      <div v-if="row.targetCommitMessage" class="commit-message">
                        {{ row.targetCommitMessage }}
                      </div>
                      <span v-else class="not-available">-</span>
                    </el-descriptions-item>
                  </el-descriptions>
                </el-col>
              </el-row>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="branchName" label="Branch" min-width="200">
          <template #default="{ row }">
            <div class="branch-name">
              <el-icon><Branch /></el-icon>
              <span>{{ row.branchName }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Status" width="150">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.syncStatus)">
              {{ formatStatus(row.syncStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Source Commit" width="120">
          <template #default="{ row }">
            <code v-if="row.sourceCommitShort" class="commit-hash">{{ row.sourceCommitShort }}</code>
            <span v-else class="not-available">-</span>
          </template>
        </el-table-column>

        <el-table-column label="Target Commit" width="120">
          <template #default="{ row }">
            <code v-if="row.targetCommitShort" class="commit-hash">{{ row.targetCommitShort }}</code>
            <span v-else class="not-available">-</span>
          </template>
        </el-table-column>

        <el-table-column label="Commit Diff" width="120">
          <template #default="{ row }">
            <span v-if="row.commitDiff !== undefined && row.commitDiff !== null" class="commit-diff">
              <span v-if="row.commitDiff > 0" class="diff-positive">+{{ row.commitDiff }}</span>
              <span v-else-if="row.commitDiff < 0" class="diff-negative">{{ row.commitDiff }}</span>
              <span v-else class="diff-zero">0</span>
            </span>
            <span v-else class="not-available">-</span>
          </template>
        </el-table-column>

        <el-table-column label="Delay" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.delayFormatted" :type="getDelayType(row.delaySeconds)">
              {{ row.delayFormatted }}
            </el-tag>
            <span v-else class="not-available">-</span>
          </template>
        </el-table-column>

        <el-table-column label="Last Commit Time" width="180">
          <template #default="{ row }">
            {{ formatTime(row.sourceLastCommitAt) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { projectsApi } from '@/api/projects'
import type { BranchComparison } from '@/types'

interface Props {
  projectId: number
}

const props = defineProps<Props>()

const loading = ref(false)
const comparison = ref<BranchComparison | null>(null)
const statusFilter = ref('')
const searchText = ref('')

const loadBranches = async () => {
  loading.value = true
  try {
    const response = await projectsApi.getBranchComparison({ syncProjectId: props.projectId })
    comparison.value = response.data
  } catch (error) {
    ElMessage.error('Failed to load branch comparison')
    console.error('Load branches failed:', error)
  } finally {
    loading.value = false
  }
}

const filteredBranches = computed(() => {
  if (!comparison.value) return []

  let branches = comparison.value.branches || []

  // Filter by status
  if (statusFilter.value) {
    branches = branches.filter(b => b.syncStatus === statusFilter.value)
  }

  // Filter by search text
  if (searchText.value) {
    const search = searchText.value.toLowerCase()
    branches = branches.filter(b =>
      b.branchName.toLowerCase().includes(search)
    )
  }

  return branches
})

const filterBranches = () => {
  // Trigger computed property update
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'synced': 'success',
    'outdated': 'warning',
    'missing_in_target': 'danger',
    'extra_in_target': 'info'
  }
  return typeMap[status] || 'info'
}

const formatStatus = (status: string) => {
  const statusMap: Record<string, string> = {
    'synced': 'Synced',
    'outdated': 'Outdated',
    'missing_in_target': 'Missing',
    'extra_in_target': 'Extra'
  }
  return statusMap[status] || status
}

const getDelayType = (delaySeconds: number | undefined) => {
  if (!delaySeconds) return 'success'
  if (delaySeconds < 3600) return 'success'
  if (delaySeconds < 86400) return 'warning'
  return 'danger'
}

const formatTime = (time: string | null | undefined) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

onMounted(() => {
  loadBranches()
})
</script>

<style scoped>
.branches-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.branch-stats {
  margin-bottom: 0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 8px;
}

.stat-icon.success {
  background-color: #f0f9ff;
  color: #52c41a;
}

.stat-icon.warning {
  background-color: #fffbe6;
  color: #faad14;
}

.stat-icon.danger {
  background-color: #fff1f0;
  color: #ff4d4f;
}

.stat-icon.info {
  background-color: #e6f4ff;
  color: #1890ff;
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
  font-weight: 600;
  color: #333;
}

.filter-bar {
  margin-bottom: 0;
}

.branches-table {
  margin-bottom: 0;
}

.branch-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.commit-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.commit-hash {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  background-color: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
  color: #666;
}

.commit-count {
  font-size: 12px;
  color: #999;
}

.commit-diff {
  font-weight: 600;
  font-size: 14px;
}

.diff-positive {
  color: #52c41a;
}

.diff-negative {
  color: #ff4d4f;
}

.diff-zero {
  color: #999;
}

.not-available {
  color: #999;
}

.branch-details {
  padding: 16px 24px;
  background-color: #fafafa;
}

.branch-details h4 {
  margin-top: 0;
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.commit-hash-full {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  background-color: #f5f5f5;
  padding: 4px 8px;
  border-radius: 3px;
  color: #666;
  word-break: break-all;
}

.commit-message {
  line-height: 1.5;
  color: #333;
  word-break: break-word;
}
</style>
