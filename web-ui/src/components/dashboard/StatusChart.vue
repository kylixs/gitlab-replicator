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

const colors = {
  synced: '#52c41a',
  syncing: '#1890ff',
  pending: '#faad14',
  paused: '#8c8c8c',
  failed: '#f5222d'
}

const initChart = () => {
  if (!chartRef.value) return

  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chartInstance || !props.data) return

  const chartData = [
    { name: 'Synced', value: props.data.synced, itemStyle: { color: colors.synced } },
    { name: 'Syncing', value: props.data.syncing, itemStyle: { color: colors.syncing } },
    { name: 'Pending', value: props.data.pending, itemStyle: { color: colors.pending } },
    { name: 'Paused', value: props.data.paused, itemStyle: { color: colors.paused } },
    { name: 'Failed', value: props.data.failed, itemStyle: { color: colors.failed } }
  ]

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
