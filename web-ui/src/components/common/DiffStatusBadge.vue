<template>
  <el-tag
    :type="getTagType(diffStatus)"
    :effect="getTagEffect(diffStatus)"
    size="small"
    class="diff-status-badge"
  >
    <el-icon :size="14" class="badge-icon">
      <component :is="getIcon(diffStatus)" />
    </el-icon>
    {{ getStatusText(diffStatus) }}
  </el-tag>
</template>

<script setup lang="ts">
import { CircleCheck, Warning, QuestionFilled, Promotion, Remove } from '@element-plus/icons-vue'

interface Props {
  diffStatus?: string
}

const props = defineProps<Props>()

const getTagType = (status?: string) => {
  if (!status) return 'info'
  const statusLower = status.toLowerCase()

  switch (statusLower) {
    case 'synced':
      return 'success'
    case 'outdated':
      return 'warning'
    case 'ahead':
      return 'warning'
    case 'diverged':
      return 'danger'
    case 'missing':
    case 'pending':
      return 'info'
    default:
      return 'info'
  }
}

const getTagEffect = (status?: string) => {
  if (!status) return 'plain'
  const statusLower = status.toLowerCase()

  // Use 'dark' effect for critical states
  if (statusLower === 'diverged' || statusLower === 'missing') {
    return 'dark'
  }

  return 'plain'
}

const getIcon = (status?: string) => {
  if (!status) return QuestionFilled
  const statusLower = status.toLowerCase()

  switch (statusLower) {
    case 'synced':
      return CircleCheck
    case 'outdated':
      return Warning
    case 'ahead':
      return Promotion
    case 'diverged':
      return Warning
    case 'missing':
      return Remove
    case 'pending':
      return QuestionFilled
    default:
      return QuestionFilled
  }
}

const getStatusText = (status?: string) => {
  if (!status) return 'Unknown'
  const statusLower = status.toLowerCase()

  switch (statusLower) {
    case 'synced':
      return 'Synced'
    case 'outdated':
      return 'Outdated'
    case 'ahead':
      return 'Ahead'
    case 'diverged':
      return 'Diverged'
    case 'missing':
      return 'Missing'
    case 'pending':
      return 'Pending'
    default:
      return status
  }
}
</script>

<style scoped>
.diff-status-badge {
  display: inline-flex !important;
  align-items: center;
  font-weight: 500;
  white-space: nowrap;
}

.badge-icon {
  margin-right: 4px;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.diff-status-badge :deep(.el-tag__content) {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  flex-wrap: nowrap;
}
</style>
