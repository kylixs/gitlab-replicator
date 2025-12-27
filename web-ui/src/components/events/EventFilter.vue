<template>
  <el-card class="event-filter" shadow="never">
    <el-form :inline="true" :model="filters" class="filter-form">
      <el-form-item label="Event Type">
        <el-select
          v-model="filters.eventType"
          placeholder="All Types"
          clearable
          style="width: 180px"
        >
          <el-option label="Incremental Sync" value="incremental_sync" />
          <el-option label="Full Sync" value="full_sync" />
          <el-option label="Manual Sync" value="manual_sync" />
          <el-option label="Webhook Sync" value="webhook_sync" />
          <el-option label="Status Change" value="status_change" />
          <el-option label="Discovery" value="discovery" />
        </el-select>
      </el-form-item>

      <el-form-item label="Status">
        <el-select
          v-model="filters.status"
          placeholder="All Status"
          clearable
          style="width: 150px"
        >
          <el-option label="Success" value="success" />
          <el-option label="Failed" value="failed" />
          <el-option label="Running" value="running" />
        </el-select>
      </el-form-item>

      <el-form-item label="Date Range">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="To"
          start-placeholder="Start date"
          end-placeholder="End date"
          style="width: 280px"
          @change="handleDateRangeChange"
        />
      </el-form-item>

      <el-form-item label="Search">
        <el-input
          v-model="filters.search"
          placeholder="Project name"
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
import { ref, reactive, watch } from 'vue'

interface Filters {
  eventType: string
  status: string
  startDate: string
  endDate: string
  search: string
}

interface Props {
  modelValue: Filters
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [filters: Filters]
  search: []
  export: []
}>()

const dateRange = ref<[Date, Date] | null>(null)

const filters = reactive<Filters>({
  eventType: props.modelValue.eventType || '',
  status: props.modelValue.status || '',
  startDate: props.modelValue.startDate || '',
  endDate: props.modelValue.endDate || '',
  search: props.modelValue.search || ''
})

watch(filters, (newFilters) => {
  emit('update:modelValue', { ...newFilters })
})

const handleDateRangeChange = (value: [Date, Date] | null) => {
  if (value && value[0] && value[1]) {
    filters.startDate = value[0].toISOString().split('T')[0] || ''
    filters.endDate = value[1].toISOString().split('T')[0] || ''
  } else {
    filters.startDate = ''
    filters.endDate = ''
  }
}

const handleSearch = () => {
  emit('search')
}

const handleReset = () => {
  filters.eventType = ''
  filters.status = ''
  filters.startDate = ''
  filters.endDate = ''
  filters.search = ''
  dateRange.value = null
  emit('search')
}

const handleExport = () => {
  emit('export')
}
</script>

<style scoped>
.event-filter {
  margin-bottom: 16px;
}

.filter-form {
  margin: 0;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 16px;
}
</style>
