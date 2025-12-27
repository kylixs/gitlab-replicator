<template>
  <el-card class="event-type-trend-chart">
    <template #header>
      <div class="chart-header">
        <span>Event Type Trends</span>
        <el-radio-group v-model="timeRange" size="small" @change="handleTimeRangeChange">
          <el-radio-button value="24h">Last 24 Hours</el-radio-button>
          <el-radio-button value="7d">Last 7 Days</el-radio-button>
        </el-radio-group>
      </div>
    </template>
    <div ref="chartRef" style="height: 300px"></div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import * as echarts from 'echarts'

interface Props {
  eventTypeTrend: {
    dates: string[]
    typeData: Record<string, number[]>
  } | null
  eventTypeTrend24h: {
    dates: string[]
    typeData: Record<string, number[]>
  } | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'timeRangeChange', range: '24h' | '7d'): void
}>()

const chartRef = ref<HTMLElement | null>(null)
const timeRange = ref<'24h' | '7d'>('24h')
let chartInstance: echarts.ECharts | null = null

const handleTimeRangeChange = (range: '24h' | '7d') => {
  emit('timeRangeChange', range)
}

// Event type color mapping
const eventTypeColors: Record<string, string> = {
  'sync_finished': '#67c23a',
  'sync_failed': '#f56c6c',
  'task_blocked': '#e6a23c',
  'task_recovered': '#409eff',
  'target_project_created': '#909399',
  'webhook_sync': '#b37feb',
  'scheduled_sync': '#52c41a',
  'manual_sync': '#1890ff'
}

// Event type display name mapping
const eventTypeNames: Record<string, string> = {
  'sync_finished': 'Sync Finished',
  'sync_failed': 'Sync Failed',
  'task_blocked': 'Task Blocked',
  'task_recovered': 'Task Recovered',
  'target_project_created': 'Target Created',
  'webhook_sync': 'Webhook Sync',
  'scheduled_sync': 'Scheduled Sync',
  'manual_sync': 'Manual Sync'
}

const formatXAxisData = computed(() => {
  if (timeRange.value === '24h' && props.eventTypeTrend24h) {
    return props.eventTypeTrend24h.dates.map(h => {
      const hour = parseInt(h)
      return `${hour}:00`
    })
  } else if (props.eventTypeTrend) {
    return props.eventTypeTrend.dates.map(d => {
      const date = new Date(d)
      return `${date.getMonth() + 1}/${date.getDate()}`
    })
  }
  return []
})

const currentData = computed(() => {
  if (timeRange.value === '24h' && props.eventTypeTrend24h) {
    return props.eventTypeTrend24h.typeData
  } else if (props.eventTypeTrend) {
    return props.eventTypeTrend.typeData
  }
  return null
})

const initChart = () => {
  if (!chartRef.value || !currentData.value) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(chartRef.value)

  // Build series from typeData
  const series = Object.entries(currentData.value).map(([eventType, counts]) => ({
    name: eventTypeNames[eventType] || eventType,
    type: 'line',
    data: counts,
    smooth: true,
    lineStyle: {
      width: 2,
      color: eventTypeColors[eventType] || '#909399'
    },
    itemStyle: {
      color: eventTypeColors[eventType] || '#909399'
    }
  }))

  const legendData = Object.keys(currentData.value).map(
    eventType => eventTypeNames[eventType] || eventType
  )

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      data: legendData,
      bottom: 0,
      type: 'scroll'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: formatXAxisData.value
    },
    yAxis: {
      type: 'value',
      name: 'Count',
      minInterval: 1
    },
    series: series
  }

  chartInstance.setOption(option)
}

onMounted(() => {
  initChart()

  window.addEventListener('resize', () => {
    chartInstance?.resize()
  })
})

watch([() => props.eventTypeTrend, () => props.eventTypeTrend24h, timeRange], () => {
  initChart()
}, { deep: true })
</script>

<style scoped>
.event-type-trend-chart {
  height: 100%;
}

.event-type-trend-chart :deep(.el-card__header) {
  padding: 16px 20px;
  font-weight: 600;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.event-type-trend-chart :deep(.el-card__body) {
  padding: 20px;
}
</style>
