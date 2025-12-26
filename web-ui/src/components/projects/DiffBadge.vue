<template>
  <div class="diff-badge">
    <!-- Diff Status Badge -->
    <div v-if="diff.diffStatus" class="diff-item">
      <DiffStatusBadge :diff-status="diff.diffStatus" />
    </div>

    <!-- Branch Diff Details -->
    <div class="diff-item">
      <span class="diff-label">Branches:</span>
      <span v-if="diff.branchNew > 0" class="diff-value new">+{{ diff.branchNew }}</span>
      <span v-if="diff.branchDeleted > 0" class="diff-value deleted">-{{ diff.branchDeleted }}</span>
      <span v-if="diff.branchOutdated > 0" class="diff-value outdated">~{{ diff.branchOutdated }}</span>
      <span v-if="!hasBranchDiff" class="diff-value synced">Synced</span>
    </div>

    <!-- Commit Diff (only if significant) -->
    <div v-if="diff.commitDiff > 0" class="diff-item">
      <span class="diff-label">Commits:</span>
      <span class="diff-value new">+{{ diff.commitDiff }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DiffStatusBadge from '@/components/common/DiffStatusBadge.vue'

interface DiffInfo {
  diffStatus?: string
  branchNew: number
  branchDeleted: number
  branchOutdated: number
  commitDiff: number
}

interface Props {
  diff: DiffInfo
}

const props = defineProps<Props>()

const hasBranchDiff = computed(() => {
  return props.diff.branchNew > 0 ||
         props.diff.branchDeleted > 0 ||
         props.diff.branchOutdated > 0
})
</script>

<style scoped>
.diff-badge {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}

.diff-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.diff-label {
  color: #666;
  font-weight: 500;
}

.diff-value {
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 600;
  font-size: 12px;
}

.diff-value.new {
  background-color: #f6ffed;
  color: #52c41a;
}

.diff-value.deleted {
  background-color: #fff1f0;
  color: #f5222d;
}

.diff-value.outdated {
  background-color: #fffbe6;
  color: #faad14;
}

.diff-value.synced {
  background-color: #f0f0f0;
  color: #8c8c8c;
}
</style>
