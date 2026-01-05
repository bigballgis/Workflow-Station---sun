<template>
  <div class="process-stats-widget">
    <div class="chart-container" ref="chartRef"></div>
    <div class="stats-summary">
      <div class="summary-item">
        <span class="label">{{ $t('dashboard.initiatedProcesses') }}</span>
        <span class="value">{{ stats.initiated }}</span>
      </div>
      <div class="summary-item">
        <span class="label">{{ $t('dashboard.inProgressProcesses') }}</span>
        <span class="value">{{ stats.inProgress }}</span>
      </div>
      <div class="summary-item">
        <span class="label">{{ $t('dashboard.completedThisMonth') }}</span>
        <span class="value">{{ stats.completed }}</span>
      </div>
      <div class="summary-item">
        <span class="label">{{ $t('dashboard.approvalRate') }}</span>
        <span class="value highlight">{{ stats.approvalRate }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { useDashboardStore } from '@/stores/dashboard'

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const dashboardStore = useDashboardStore()

const stats = ref({
  initiated: 0,
  inProgress: 0,
  completed: 0,
  approvalRate: 0
})

const initChart = () => {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)
  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      bottom: '0',
      left: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: {
        fontSize: 11
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '40%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false
        },
        data: [
          { value: stats.value.inProgress, name: '进行中', itemStyle: { color: '#FF6600' } },
          { value: stats.value.completed, name: '已完成', itemStyle: { color: '#00A651' } },
          { value: 5, name: '已拒绝', itemStyle: { color: '#DB0011' } }
        ]
      }
    ]
  }
  chart.setOption(option)
}

const handleResize = () => {
  chart?.resize()
}

onMounted(async () => {
  await dashboardStore.fetchOverview()
  stats.value = {
    initiated: dashboardStore.overview?.initiatedProcesses || 0,
    inProgress: dashboardStore.overview?.inProgressProcesses || 0,
    completed: dashboardStore.overview?.completedThisMonth || 0,
    approvalRate: dashboardStore.overview?.approvalRate || 0
  }
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  chart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped lang="scss">
.process-stats-widget {
  height: 100%;
  display: flex;
  flex-direction: column;

  .chart-container {
    flex: 1;
    min-height: 120px;
  }

  .stats-summary {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    padding-top: 10px;
    border-top: 1px solid #f0f0f0;

    .summary-item {
      display: flex;
      flex-direction: column;
      align-items: center;

      .label {
        font-size: 11px;
        color: #909399;
      }

      .value {
        font-size: 16px;
        font-weight: 600;
        color: #303133;

        &.highlight {
          color: #00A651;
        }
      }
    }
  }
}
</style>
