/**
 * Dashboard 仪表盘业务逻辑 composable
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
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
    catch (e) { console.error('Failed to load stats:', e) }
    finally { statsLoading.value = false }
  }

  const loadActivities = async () => {
    activitiesLoading.value = true
    try { activities.value = await getRecentActivities(10) }
    catch (e) { console.error('Failed to load activities:', e) }
    finally { activitiesLoading.value = false }
  }

  const loadTrends = async () => {
    trendsLoading.value = true
    try { trends.value = await getUserTrends(7); updateChart() }
    catch (e) { console.error('Failed to load trends:', e) }
    finally { trendsLoading.value = false }
  }

  const updateChart = () => {
    if (!chart || trends.value.length === 0) return
    const dates = trends.value.map(t => t.date)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: [t('dashboard.activeUsers'), t('dashboard.newUsers')] },
      xAxis: { type: 'category', data: dates },
      yAxis: { type: 'value' },
      series: [
        { name: t('dashboard.activeUsers'), type: 'line', data: trends.value.map(t => t.activeUsers), smooth: true },
        { name: t('dashboard.newUsers'), type: 'bar', data: trends.value.map(t => t.newUsers) }
      ]
    })
  }

  onMounted(async () => {
    if (systemChartRef.value) chart = echarts.init(systemChartRef.value)
    await Promise.all([loadStats(), loadActivities(), loadTrends()])
    refreshTimer = window.setInterval(() => { loadStats(); loadActivities() }, 60000)
  })

  onUnmounted(() => {
    if (refreshTimer) clearInterval(refreshTimer)
    chart?.dispose()
  })

  return { systemChartRef, statsLoading, activitiesLoading, trendsLoading, statsCards, activities, loadStats, loadActivities }
}
