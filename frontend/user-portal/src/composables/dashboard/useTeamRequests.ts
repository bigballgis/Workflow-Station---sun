import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { getTeamRequests, TeamRequestsResponse } from '@/api/dashboard'

// 团队请求弹窗：状态、分页、加载与状态标签辅助
export function useTeamRequests() {
  const { t } = useI18n()

  const teamDialogVisible = ref(false)
  const teamLoading = ref(false)
  const teamActiveTab = ref('all')
  const teamPagination = reactive({ page: 1, size: 10 })
  const teamRequests = ref<TeamRequestsResponse>({
    overallCount: 0,
    runningCount: 0,
    completedCount: 0,
    withdrawnCount: 0,
    content: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    size: 10
  })

  const openTeamRequestsDialog = async () => {
    teamActiveTab.value = 'all'
    teamPagination.page = 1
    teamDialogVisible.value = true
    await loadTeamRequests()
  }

  const loadTeamRequests = async () => {
    teamLoading.value = true
    try {
      const status = teamActiveTab.value === 'all' ? undefined : teamActiveTab.value
      const res = await getTeamRequests({
        status,
        page: teamPagination.page - 1,
        size: teamPagination.size
      })
      const data = res.data || res
      if (data) {
        teamRequests.value = data as unknown as TeamRequestsResponse
      }
    } catch (error) {
      console.error('Failed to load team requests:', error)
    } finally {
      teamLoading.value = false
    }
  }

  const switchTeamTab = (tab: string) => {
    teamActiveTab.value = tab
    teamPagination.page = 1
    loadTeamRequests()
  }

  const handleTeamTabChange = () => {
    teamPagination.page = 1
    loadTeamRequests()
  }

  const handleTeamPageChange = () => {
    loadTeamRequests()
  }

  const getTeamStatusType = (status: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' => {
    const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
      RUNNING: 'warning',
      COMPLETED: 'success',
      WITHDRAWN: 'info'
    }
    return map[status] || 'info'
  }

  const getTeamStatusLabel = (status: string) => {
    const map: Record<string, string> = {
      RUNNING: t('application.running'),
      COMPLETED: t('application.completed'),
      WITHDRAWN: t('application.withdrawn')
    }
    return map[status] || status
  }

  return {
    teamDialogVisible,
    teamLoading,
    teamActiveTab,
    teamPagination,
    teamRequests,
    openTeamRequestsDialog,
    loadTeamRequests,
    switchTeamTab,
    handleTeamTabChange,
    handleTeamPageChange,
    getTeamStatusType,
    getTeamStatusLabel
  }
}
