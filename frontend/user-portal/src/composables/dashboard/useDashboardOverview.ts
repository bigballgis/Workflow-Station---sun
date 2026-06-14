import { ref } from 'vue'
import { getDashboardOverview, TaskOverview, ProcessOverview, PerformanceOverview } from '@/api/dashboard'
import { usePendingTaskStore } from '@/stores/pendingTask'

// 仪表盘概览数据加载：任务 / 流程 / 个人绩效 / 最近任务
export function useDashboardOverview() {
  const pendingTaskStore = usePendingTaskStore()

  const loading = ref(true)

  const taskOverview = ref<TaskOverview>({
    pendingCount: 0,
    overdueCount: 0,
    completedTodayCount: 0,
    avgProcessingHours: 0,
    urgentCount: 0,
    highPriorityCount: 0,
    teamPendingCount: 0,
    teamOverdueCount: 0,
    teamCompletedTodayCount: 0
  })

  const processOverview = ref<ProcessOverview>({
    initiatedCount: 0,
    inProgressCount: 0,
    completedThisMonthCount: 0,
    approvalRate: 0,
    typeDistribution: {}
  })

  const performanceOverview = ref<PerformanceOverview>({
    efficiencyScore: 0,
    qualityScore: 0,
    collaborationScore: 0,
    monthlyRank: 0,
    totalUsers: 0
  })

  const recentTasks = ref<any[]>([])

  const loadDashboardData = async () => {
    loading.value = true
    try {
      const res = await getDashboardOverview()
      // API 返回格式: { success: true, data: { taskOverview, processOverview, performanceOverview, recentTasks } }
      const data = res.data || res
      if (data) {
        taskOverview.value = data.taskOverview || taskOverview.value
        processOverview.value = data.processOverview || processOverview.value
        performanceOverview.value = data.performanceOverview || performanceOverview.value
        recentTasks.value = data.recentTasks || []
        if (data.taskOverview != null && typeof data.taskOverview.pendingCount === 'number') {
          pendingTaskStore.syncCountFromListTotal(data.taskOverview.pendingCount)
        }
      }
    } catch (error) {
      console.error('Failed to load dashboard data:', error)
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    taskOverview,
    processOverview,
    performanceOverview,
    recentTasks,
    loadDashboardData
  }
}
