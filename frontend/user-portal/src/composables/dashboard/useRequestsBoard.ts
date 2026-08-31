import { ref } from 'vue'
import { getTeamRequests, type TeamRequestItem } from '@/api/dashboard'
import { processApi, type ProcessInstance } from '@/api/process'

/** 首页只做预览，明细各自去 My Requests / Team Requests 看。 */
const PREVIEW_SIZE = 5

export interface TeamRequestSummary {
  overallCount: number
  runningCount: number
  completedCount: number
  withdrawnCount: number
}

/**
 * 首页「我的申请 / 团队申请」两块看板的数据。
 * 两个来源各自成败：一个挂了不该把另一个也清空，但任一失败都要让页面说出来，
 * 否则用户看到的 0 和「真的没有申请」长得一模一样。
 */
export function useRequestsBoard() {
  const loading = ref(true)
  const loadFailed = ref(false)

  const myRequests = ref<ProcessInstance[]>([])
  const myRequestsTotal = ref(0)

  const teamSummary = ref<TeamRequestSummary>({
    overallCount: 0,
    runningCount: 0,
    completedCount: 0,
    withdrawnCount: 0
  })
  const teamRecent = ref<TeamRequestItem[]>([])

  const loadRequestsBoard = async () => {
    loading.value = true
    loadFailed.value = false

    const [mine, team] = await Promise.allSettled([
      processApi.queryMyApplications({ page: 0, size: PREVIEW_SIZE }),
      getTeamRequests({ page: 0, size: PREVIEW_SIZE })
    ])

    if (mine.status === 'fulfilled') {
      const page = mine.value.data
      myRequests.value = page?.content || []
      myRequestsTotal.value = page?.totalElements ?? 0
    } else {
      loadFailed.value = true
      console.error('Failed to load my requests:', mine.reason)
    }

    if (team.status === 'fulfilled') {
      const res = team.value as unknown as { data?: unknown }
      const data = (res.data || res) as (TeamRequestSummary & { content?: TeamRequestItem[] }) | null
      if (data) {
        teamSummary.value = {
          overallCount: data.overallCount ?? 0,
          runningCount: data.runningCount ?? 0,
          completedCount: data.completedCount ?? 0,
          withdrawnCount: data.withdrawnCount ?? 0
        }
        teamRecent.value = data.content || []
      }
    } else {
      loadFailed.value = true
      console.error('Failed to load team requests:', team.reason)
    }

    loading.value = false
  }

  return {
    loading,
    loadFailed,
    myRequests,
    myRequestsTotal,
    teamSummary,
    teamRecent,
    loadRequestsBoard
  }
}
