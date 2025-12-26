<template>
  <div class="overview-tab">
    <!-- Status Cards -->
    <el-row :gutter="16" class="status-cards">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon success">
              <el-icon :size="24"><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Status</div>
              <el-tag :type="getStatusType(overview.project.syncStatus)">
                {{ overview.project.syncStatus }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon warning">
              <el-icon :size="24"><Timer /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Delay</div>
              <div class="stat-value">{{ overview.delay?.formatted || '-' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon info">
              <el-icon :size="24"><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Last Sync</div>
              <div class="stat-value">{{ formatTime(overview.project.lastSyncAt) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon primary">
              <el-icon :size="24"><Calendar /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">Next Sync</div>
              <div class="stat-value">{{ formatTime(overview.nextSyncTime) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Diff Status Badge -->
    <el-card v-if="overview.diff?.diffStatus" shadow="hover" class="diff-status-card">
      <div class="diff-status-container">
        <div class="diff-status-label">Overall Diff Status</div>
        <el-tag :type="getDiffStatusType(overview.diff.diffStatus)" size="large" class="diff-status-badge">
          <el-icon class="status-icon">
            <component :is="getDiffStatusIcon(overview.diff.diffStatus)" />
          </el-icon>
          {{ getDiffStatusLabel(overview.diff.diffStatus) }}
        </el-tag>
      </div>
    </el-card>

    <!-- Diff Statistics -->
    <el-row :gutter="16" class="diff-stats">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">New Branches</div>
            <div class="diff-value new">+{{ overview.diff?.branchNew || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Deleted Branches</div>
            <div class="diff-value deleted">-{{ overview.diff?.branchDeleted || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Outdated Branches</div>
            <div class="diff-value outdated">~{{ overview.diff?.branchOutdated || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Ahead Branches</div>
            <div class="diff-value ahead">↑{{ overview.diff?.branchAhead || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Diverged Branches</div>
            <div class="diff-value diverged">⚡{{ overview.diff?.branchDiverged || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="diff-card">
          <div class="diff-item">
            <div class="diff-label">Commit Diff</div>
            <div class="diff-value new">+{{ overview.diff?.commitDiff || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Project Information -->
    <el-row :gutter="16" class="project-info-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>Source Project</span>
              <el-tag type="info" size="small">GitLab</el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Project ID">
              {{ overview.source?.id }}
            </el-descriptions-item>
            <el-descriptions-item label="Name">
              {{ overview.source?.name }}
            </el-descriptions-item>
            <el-descriptions-item label="Path">
              {{ overview.source?.pathWithNamespace }}
            </el-descriptions-item>
            <el-descriptions-item label="Default Branch">
              {{ overview.source?.defaultBranch }}
            </el-descriptions-item>
            <el-descriptions-item label="Last Activity">
              {{ formatTime(overview.source?.lastActivityAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="Commit Count">
              {{ overview.source?.commitCount || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>Target Project</span>
              <el-tag type="success" size="small">GitLab</el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Project ID">
              {{ overview.target?.id }}
            </el-descriptions-item>
            <el-descriptions-item label="Name">
              {{ overview.target?.name }}
            </el-descriptions-item>
            <el-descriptions-item label="Path">
              {{ overview.target?.pathWithNamespace }}
            </el-descriptions-item>
            <el-descriptions-item label="Default Branch">
              {{ overview.target?.defaultBranch }}
            </el-descriptions-item>
            <el-descriptions-item label="Last Activity">
              {{ formatTime(overview.target?.lastActivityAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="Commit Count">
              {{ overview.target?.commitCount || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- Sync Configuration -->
    <el-card shadow="hover" class="sync-config">
      <template #header>
        <span>Sync Configuration</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Sync Method">
          <el-tag type="info">
            {{ overview.project.syncMethod === 'push_mirror' ? 'Push Mirror' : 'Pull Sync' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Created At">
          {{ formatTime(overview.project.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Updated At">
          {{ formatTime(overview.project.updatedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="Last Sync At">
          {{ formatTime(overview.project.lastSyncAt) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- Cache Information -->
    <el-card v-if="overview.cache" shadow="hover" class="cache-info">
      <template #header>
        <div class="card-header">
          <span>Git Cache Information</span>
          <el-tag :type="overview.cache.exists ? 'success' : 'info'" size="small">
            {{ overview.cache.exists ? 'Exists' : 'Not Found' }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Cache Path" :span="2">
          <code>{{ overview.cache.path || '-' }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="Cache Size">
          {{ overview.cache.sizeFormatted || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="Last Modified">
          {{ formatTime(overview.cache.lastModified) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { SuccessFilled, WarningFilled, CircleCloseFilled, QuestionFilled } from '@element-plus/icons-vue'
import type { ProjectOverview } from '@/types'

interface Props {
  overview: ProjectOverview
}

defineProps<Props>()

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

const getDiffStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'SYNCED': 'success',
    'OUTDATED': 'warning',
    'AHEAD': 'warning',
    'DIVERGED': 'danger',
    'INCONSISTENT': 'danger',
    'PENDING': 'info',
    'SOURCE_MISSING': 'danger'
  }
  return typeMap[status] || 'info'
}

const getDiffStatusLabel = (status: string) => {
  const labelMap: Record<string, string> = {
    'SYNCED': 'Synced',
    'OUTDATED': 'Outdated',
    'AHEAD': 'Ahead',
    'DIVERGED': 'Diverged',
    'INCONSISTENT': 'Inconsistent',
    'PENDING': 'Pending',
    'SOURCE_MISSING': 'Source Missing'
  }
  return labelMap[status] || status
}

const getDiffStatusIcon = (status: string) => {
  const iconMap: Record<string, any> = {
    'SYNCED': SuccessFilled,
    'OUTDATED': WarningFilled,
    'AHEAD': WarningFilled,
    'DIVERGED': CircleCloseFilled,
    'INCONSISTENT': CircleCloseFilled,
    'PENDING': QuestionFilled,
    'SOURCE_MISSING': CircleCloseFilled
  }
  return iconMap[status] || QuestionFilled
}

const formatTime = (time: string | null | undefined) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}
</script>

<style scoped>
.overview-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-cards,
.diff-stats,
.project-info-row {
  margin-bottom: 16px;
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

.stat-icon.info {
  background-color: #e6f4ff;
  color: #1890ff;
}

.stat-icon.primary {
  background-color: #f0f5ff;
  color: #2f54eb;
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
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.diff-card {
  text-align: center;
}

.diff-item {
  padding: 8px 0;
}

.diff-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.diff-value {
  font-size: 32px;
  font-weight: bold;
}

.diff-value.new {
  color: #52c41a;
}

.diff-value.deleted {
  color: #ff4d4f;
}

.diff-value.outdated {
  color: #faad14;
}

.diff-value.ahead {
  color: #fa8c16;
}

.diff-value.diverged {
  color: #f5222d;
}

.diff-status-card {
  margin-bottom: 16px;
}

.diff-status-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.diff-status-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.diff-status-badge {
  font-size: 16px;
  padding: 8px 16px;
}

.status-icon {
  margin-right: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sync-config {
  margin-bottom: 0;
}
</style>
