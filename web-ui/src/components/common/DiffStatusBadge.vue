<template>
  <el-tag
    :type="getTagType(diffStatus)"
    :effect="getTagEffect(diffStatus)"
    size="small"
    class="diff-status-badge"
  >
    <el-icon :size="14" style="margin-right: 4px">
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
    case 'source_missing':
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
  if (statusLower === 'diverged' || statusLower === 'source_missing') {
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
    case 'source_missing':
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
    case 'source_missing':
      return 'Source Missing'
    case 'pending':
      return 'Pending'
    default:
      return status
  }
}
</script>

<style scoped>
.diff-status-badge {
  display: inline-flex;
  align-items: center;
  font-weight: 500;
}
</style>
