<template>
  <el-card class="delayed-table">
    <template #header>
      <span>Top 10 Delayed Projects</span>
    </template>
    <div class="table-container">
      <el-table
        v-if="projects && projects.length > 0"
        :data="projects"
        stripe
        style="width: 100%"
        max-height="300"
      >
        <el-table-column prop="projectKey" label="Project" min-width="200" />
        <el-table-column label="Delay" width="150">
          <template #default="{ row }">
            <el-tag :type="getDelayType(row.delaySeconds)">
              {{ row.delayFormatted }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="syncStatus" label="Status" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.syncStatus)">
              {{ row.syncStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleViewDetail(row)">
              Detail
            </el-button>
            <el-button size="small" type="primary" @click="handleSync(row)">
              Sync
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-state">
        <el-empty description="No delayed projects" />
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { DelayedProject } from '@/types'

interface Props {
  projects: DelayedProject[] | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  viewDetail: [project: DelayedProject]
  sync: [project: DelayedProject]
}>()

const getDelayType = (delaySeconds: number) => {
  if (delaySeconds < 3600) return 'success' // < 1 hour
  if (delaySeconds < 86400) return 'warning' // < 1 day
  return 'danger' // > 1 day
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    'synced': 'success',
    'syncing': 'info',
    'paused': 'info',
    'failed': 'danger',
    'outdated': 'warning'
  }
  return typeMap[status.toLowerCase()] || 'info'
}

const handleViewDetail = (project: DelayedProject) => {
  emit('viewDetail', project)
}

const handleSync = (project: DelayedProject) => {
  emit('sync', project)
}
</script>

<style scoped>
.delayed-table {
  border-radius: 8px;
}

.table-container {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
