/**
 * Dashboard 仪表盘业务逻辑 composable
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { logger } from '@/utils/logger'
import * as echarts from 'echarts'
import { getStats, getRecentActivities, getUserTrends, type DashboardStats, type RecentActivity, type UserTrend } from '@/api/dashboard'

export function useDashboard() {
  const { t } = useI18n()

  const systemChartRef = ref<HTMLElement>()
  let chart: echarts.ECharts | null = null
  let refreshTimer: number | null = null

  const statsLoading = ref(false)
  const activitiesLoading = ref(false)
  const trendsLoading = ref(false)

  const stats = ref<DashboardStats | null>(null)
  const activities = ref<RecentActivity[]>([])
  const trends = ref<UserTrend[]>([])

  const statsCards = computed(() => {
    if (!stats.value) {
      return [
        { titleKey: 'dashboard.totalUsers', value: '-', icon: 'User', color: '#409EFF' },
        { titleKey: 'dashboard.totalBusinessUnits', value: '-', icon: 'OfficeBuilding', color: '#67C23A' },
        { titleKey: 'dashboard.totalRoles', value: '-', icon: 'Key', color: '#E6A23C' },
        { titleKey: 'dashboard.onlineUsers', value: '-', icon: 'Connection', color: '#F56C6C' }
      ]
    }
    return [
      { titleKey: 'dashboard.totalUsers', value: stats.value.totalUsers.toLocaleString(), icon: 'User', color: '#409EFF' },
      { titleKey: 'dashboard.totalBusinessUnits', value: stats.value.totalBusinessUnits.toLocaleString(), icon: 'OfficeBuilding', color: '#67C23A' },
      { titleKey: 'dashboard.totalRoles', value: stats.value.totalRoles.toLocaleString(), icon: 'Key', color: '#E6A23C' },
      { titleKey: 'dashboard.onlineUsers', value: stats.value.onlineUsers.toLocaleString(), icon: 'Connection', color: '#F56C6C' }
    ]
  })

  const loadStats = async () => {
    statsLoading.value = true
    try { stats.value = await getStats() }
    catch (e) { logger.error('dashboard', 'Failed to load stats:', e) }
    finally { statsLoading.value = false }
  }

  const loadActivities = async () => {
    activitiesLoading.value = true
    try { activities.value = await getRecentActivities(10) }
    catch (e) { logger.error('dashboard', 'Failed to load activities:', e) }
    finally { activitiesLoading.value = false }
  }

  const loadTrends = async () => {
    trendsLoading.value = true
    try { trends.value = await getUserTrends(7); updateChart() }
    catch (e) { logger.error('dashboard', 'Failed to load trends:', e) }
    finally { trendsLoading.value = false }
  }

  // 编辑部风格图表：品牌红折线 + 浅红渐变面积、灰柱、细线坐标轴；图例移到卡头（模板内自绘）
  const updateChart = () => {
    if (!chart || trends.value.length === 0) return
    const dates = trends.value.map(t => t.date)
    chart.setOption({
      textStyle: { fontFamily: "'Inter Variable', -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif" },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#ffffff',
        borderColor: '#ececea',
        textStyle: { color: '#1a1a1a', fontSize: 12 },
        axisPointer: { lineStyle: { color: '#d8d8d4' } }
      },
      legend: { show: false },
      grid: { left: 8, right: 8, top: 16, bottom: 0, containLabel: true },
      xAxis: {
        type: 'category',
        data: dates,
        axisLine: { lineStyle: { color: '#ececea' } },
        axisTick: { show: false },
        axisLabel: { color: '#9c9c9c', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f0f0ee' } },
        axisLabel: { color: '#9c9c9c', fontSize: 11 }
      },
      series: [
        {
          name: t('dashboard.activeUsers'),
          type: 'line',
          data: trends.value.map(t => t.activeUsers),
          smooth: true,
          lineStyle: { color: '#db0011', width: 2 },
          itemStyle: { color: '#db0011' },
          symbol: 'circle',
          symbolSize: 6,
          showSymbol: false,
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(219, 0, 17, 0.10)' },
              { offset: 1, color: 'rgba(219, 0, 17, 0)' }
            ])
          }
        },
        {
          name: t('dashboard.newUsers'),
          type: 'bar',
          data: trends.value.map(t => t.newUsers),
          barWidth: 12,
          itemStyle: { color: '#e0e0dc', borderRadius: [3, 3, 0, 0] },
          emphasis: { itemStyle: { color: '#161616' } }
        }
      ]
    })
  }

  const handleResize = () => { chart?.resize() }

  onMounted(async () => {
    if (systemChartRef.value) chart = echarts.init(systemChartRef.value)
    window.addEventListener('resize', handleResize)
    await Promise.all([loadStats(), loadActivities(), loadTrends()])
    refreshTimer = window.setInterval(() => { loadStats(); loadActivities() }, 60000)
  })

  onUnmounted(() => {
    if (refreshTimer) clearInterval(refreshTimer)
    window.removeEventListener('resize', handleResize)
    chart?.dispose()
  })

  return { systemChartRef, statsLoading, activitiesLoading, trendsLoading, statsCards, activities, loadStats, loadActivities }
}
