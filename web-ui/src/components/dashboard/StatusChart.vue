<template>
  <el-card class="status-chart">
    <template #header>
      <div class="chart-header">
        <span>Status Distribution</span>
        <el-radio-group v-model="chartType" size="small">
          <el-radio-button value="pie">Pie Chart</el-radio-button>
          <el-radio-button value="bar">Bar Chart</el-radio-button>
        </el-radio-group>
      </div>
    </template>
    <div ref="chartRef" style="width: 100%; height: 300px"></div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import type { StatusDistribution } from '@/types'

interface Props {
  data: StatusDistribution | null
}

const props = defineProps<Props>()
const chartRef = ref<HTMLElement>()
const chartType = ref<'pie' | 'bar'>('pie')
let chartInstance: echarts.ECharts | null = null

// Dynamic color mapping for different statuses
const getStatusColor = (status: string): string => {
  const colorMap: Record<string, string> = {
    'active': '#52c41a',
    'pending': '#1890ff',
    'missing': '#faad14',
    'failed': '#f5222d',
    'warning': '#faad14',
    'deleted': '#8c8c8c',
    'target_created': '#1890ff',
    'mirror_configured': '#1890ff'
  }
  return colorMap[status] || '#8c8c8c'
}

// Format status name for display
const formatStatusName = (status: string): string => {
  const nameMap: Record<string, string> = {
    'active': 'Active',
    'pending': 'Pending',
    'missing': 'Missing',
    'failed': 'Failed',
    'warning': 'Warning',
    'deleted': 'Deleted',
    'target_created': 'Target Created',
    'mirror_configured': 'Mirror Configured'
  }
  return nameMap[status] || status.charAt(0).toUpperCase() + status.slice(1).replace(/_/g, ' ')
}

const initChart = () => {
  if (!chartRef.value) return

  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chartInstance || !props.data || !Array.isArray(props.data)) return

  // Convert array data to chart format
  const chartData = props.data.map(item => ({
    name: formatStatusName(item.status),
    value: item.count,
    itemStyle: { color: getStatusColor(item.status) }
  }))

  const option = chartType.value === 'pie' ? {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}: {c}'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: chartData
      }
    ]
  } : {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: chartData.map(d => d.name)
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: chartData.map(d => ({
          value: d.value,
          itemStyle: d.itemStyle
        })),
        barWidth: '60%',
        label: {
          show: true,
          position: 'top'
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

watch(() => props.data, () => {
  updateChart()
}, { deep: true })

watch(chartType, () => {
  updateChart()
})

onMounted(() => {
  initChart()
  window.addEventListener('resize', () => {
    chartInstance?.resize()
  })
})

onUnmounted(() => {
  chartInstance?.dispose()
})
</script>

<style scoped>
.status-chart {
  border-radius: 8px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
</style>
