<template>
  <el-card class="filter-bar" shadow="never">
    <el-form :inline="true" :model="filters" class="filter-form">
      <el-form-item label="Group">
        <el-select
          v-model="filters.group"
          placeholder="All Groups"
          clearable
          style="width: 180px"
        >
          <el-option
            v-for="group in groups"
            :key="group"
            :label="group"
            :value="group"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="Status">
        <el-select
          v-model="filters.status"
          placeholder="All Status"
          clearable
          style="width: 180px"
        >
          <el-option label="Discovered" value="discovered" />
          <el-option label="Initializing" value="initializing" />
          <el-option label="Active" value="active" />
          <el-option label="Warning" value="warning" />
          <el-option label="Error" value="error" />
          <el-option label="Failed" value="failed" />
          <el-option label="Missing" value="missing" />
          <el-option label="Disabled" value="disabled" />
          <el-option label="Deleted" value="deleted" />
          <!-- Legacy status for compatibility -->
          <el-option label="Paused" value="paused" />
          <el-option label="Pending" value="pending" />
        </el-select>
      </el-form-item>

      <el-form-item label="Task Status">
        <el-select
          v-model="filters.taskStatus"
          placeholder="All Task Status"
          clearable
          style="width: 150px"
        >
          <el-option label="Waiting" value="waiting" />
          <el-option label="Pending" value="pending" />
          <el-option label="Running" value="running" />
          <el-option label="Blocked" value="blocked" />
          <el-option label="Disabled" value="disabled" />
          <el-option label="Failed" value="failed" />
        </el-select>
      </el-form-item>

      <el-form-item label="Diff Status">
        <el-select
          v-model="filters.diffStatus"
          placeholder="All Diff Status"
          clearable
          style="width: 150px"
        >
          <el-option label="Synced" value="SYNCED" />
          <el-option label="Outdated" value="OUTDATED" />
          <el-option label="Ahead" value="AHEAD" />
          <el-option label="Diverged" value="DIVERGED" />
          <el-option label="Source Missing" value="SOURCE_MISSING" />
          <el-option label="Pending" value="PENDING" />
        </el-select>
      </el-form-item>

      <el-form-item label="Delay Range">
        <el-select
          v-model="filters.delayRange"
          placeholder="All Delays"
          clearable
          style="width: 150px"
        >
          <el-option label="< 1 hour" value="0-3600" />
          <el-option label="1-24 hours" value="3600-86400" />
          <el-option label="1-7 days" value="86400-604800" />
          <el-option label="> 7 days" value="604800-" />
        </el-select>
      </el-form-item>

      <el-form-item label="Search">
        <el-input
          v-model="filters.search"
          placeholder="Project name or path"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSearch">Search</el-button>
        <el-button @click="handleReset">Reset</el-button>
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>
          Export CSV
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

interface Filters {
  group: string
  status: string
  taskStatus: string
  diffStatus: string
  delayRange: string
  search: string
}

interface Props {
  groups: string[]
  modelValue: Filters
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [filters: Filters]
  search: []
  export: []
}>()

const filters = reactive<Filters>({
  group: props.modelValue.group || '',
  status: props.modelValue.status || '',
  taskStatus: props.modelValue.taskStatus || '',
  diffStatus: props.modelValue.diffStatus || '',
  delayRange: props.modelValue.delayRange || '',
  search: props.modelValue.search || ''
})

watch(filters, (newFilters) => {
  emit('update:modelValue', { ...newFilters })
})

const handleSearch = () => {
  emit('search')
}

const handleReset = () => {
  filters.group = ''
  filters.status = ''
  filters.taskStatus = ''
  filters.diffStatus = ''
  filters.delayRange = ''
  filters.search = ''
  emit('search')
}

const handleExport = () => {
  emit('export')
}
</script>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
}

.filter-form {
  margin: 0;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 16px;
}
</style>
