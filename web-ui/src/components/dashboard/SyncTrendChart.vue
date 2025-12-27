<template>
  <el-card class="sync-trend-chart">
    <template #header>
      <div class="chart-header">
        <span>Sync Event Trends</span>
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
  trendData: {
    dates: string[]
    totalSyncs: number[]
    successSyncs: number[]
    failedSyncs: number[]
  } | null
  trendData24h: {
    hours: string[]
    totalSyncs: number[]
    successSyncs: number[]
    failedSyncs: number[]
  } | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'timeRangeChange', range: '24h' | '7d'): void
}>()

const chartRef = ref<HTMLElement | null>(null)
const timeRange = ref<'24h' | '7d'>('7d')
let chartInstance: echarts.ECharts | null = null

const handleTimeRangeChange = (range: '24h' | '7d') => {
  emit('timeRangeChange', range)
}

const formatXAxisData = computed(() => {
  if (timeRange.value === '24h' && props.trendData24h) {
    return props.trendData24h.hours.map(h => {
      const hour = parseInt(h)
      return `${hour}:00`
    })
  } else if (props.trendData) {
    return props.trendData.dates.map(d => {
      const date = new Date(d)
      return `${date.getMonth() + 1}/${date.getDate()}`
    })
  }
  return []
})

const currentData = computed(() => {
  if (timeRange.value === '24h' && props.trendData24h) {
    return {
      totalSyncs: props.trendData24h.totalSyncs,
      successSyncs: props.trendData24h.successSyncs,
      failedSyncs: props.trendData24h.failedSyncs
    }
  } else if (props.trendData) {
    return {
      totalSyncs: props.trendData.totalSyncs,
      successSyncs: props.trendData.successSyncs,
      failedSyncs: props.trendData.failedSyncs
    }
  }
  return null
})

const initChart = () => {
  if (!chartRef.value || !currentData.value) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(chartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      data: ['Total Syncs', 'Successful', 'Failed'],
      bottom: 0
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
    series: [
      {
        name: 'Total Syncs',
        type: 'line',
        data: currentData.value.totalSyncs,
        smooth: true,
        lineStyle: {
          width: 2,
          color: '#409eff'
        },
        itemStyle: {
          color: '#409eff'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        }
      },
      {
        name: 'Successful',
        type: 'line',
        data: currentData.value.successSyncs,
        smooth: true,
        lineStyle: {
          width: 2,
          color: '#67c23a'
        },
        itemStyle: {
          color: '#67c23a'
        }
      },
      {
        name: 'Failed',
        type: 'line',
        data: currentData.value.failedSyncs,
        smooth: true,
        lineStyle: {
          width: 2,
          color: '#f56c6c'
        },
        itemStyle: {
          color: '#f56c6c'
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

onMounted(() => {
  initChart()

  window.addEventListener('resize', () => {
    chartInstance?.resize()
  })
})

watch([() => props.trendData, () => props.trendData24h, timeRange], () => {
  initChart()
}, { deep: true })
</script>

<style scoped>
.sync-trend-chart {
  height: 100%;
}

.sync-trend-chart :deep(.el-card__header) {
  padding: 16px 20px;
  font-weight: 600;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sync-trend-chart :deep(.el-card__body) {
  padding: 20px;
}
</style>
