<template>
  <div class="branches-tab">
    <el-empty description="Branches comparison feature coming soon..." />
  </div>
</template>

<script setup lang="ts">
interface Props {
  projectId: number
}

defineProps<Props>()
</script>

<style scoped>
.branches-tab {
  padding: 40px 0;
}
</style>
